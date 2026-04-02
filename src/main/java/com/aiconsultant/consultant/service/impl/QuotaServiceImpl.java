package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.QuotaTransactionRecord;
import com.aiconsultant.consultant.entity.UserWallet;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.service.QuotaService;
import com.aiconsultant.consultant.service.QuotaTransactionRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class QuotaServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet> implements QuotaService {

    @Autowired
    private QuotaTransactionRecordService quotaTransactionRecordService;
    /** 墓碑插入走 Mapper，避免与 ChatMessageService 形成循环依赖 */
    @Autowired
    private ChatMessageMapper chatMessageMapper;

    public void killZombieChat(Long aiMsgId, Long txId, Long userId) {
        ChatMessage tombstone = new ChatMessage();
        tombstone.setId(aiMsgId);
        tombstone.setUserId(userId);
        tombstone.setCorrelationId(txId); // 核心：绑定事务ID
        tombstone.setRole(1); // Assistant
        tombstone.setStatus(1); // 1-生成失败/已废弃
        tombstone.setContent(""); // 空对话

        try {
            // 利用 id 或 correlationId 的唯一索引进行防悬挂拦截
            chatMessageMapper.insert(tombstone);
            log.info("聊天域墓碑插入成功，txId: {}", txId);
        } catch (DuplicateKeyException e) {
            log.warn("聊天域墓碑插入冲突，说明业务线程已完成落库，txId: {}", txId);
            // 如果这里冲突了，说明不能再去 Cancel 额度了，应该立刻终止对账的取消流程
            throw new RuntimeException("立碑失败，事务已达终态");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tryQuota(Long txId, Long chatId, Long userId, Integer amount) {
        // 1. 幂等性与防悬挂检查
        // 以 txId 作为唯一键，检查流水表
        QuotaTransactionRecord existRecord = quotaTransactionRecordService.getById(txId);

        if (existRecord != null) {
            // 状态为 2 代表 Cancel 已经先于 Try 执行，即“悬挂”现象
            // 状态为 0 或 1 代表 Try 或 Confirm 已经处理过，即“幂等”重传
            log.warn("Try 阶段拦截：存在历史流水记录，操作终止。txId: {}, 状态: {}", txId, existRecord.getStatus());
            return false;
        }

        // 2. 原子性额度预占（利用数据库行级锁与条件更新）
        // 核心逻辑：只有在可用额度足够时，才进行 quota 扣减与 frozen_quota 增加
        boolean updateSuccess = this.lambdaUpdate()
                .eq(UserWallet::getId, userId)
                .ge(UserWallet::getQuota, amount) // WHERE quota >= amount
                .setSql("quota = quota - " + amount)
                .setSql("frozen_quota = frozen_quota + " + amount)
                .set(UserWallet::getUpdateTime, LocalDateTime.now())
                .update();

        if (!updateSuccess) {
            log.info("Try 阶段失败：用户余额不足或账户不存在。userId: {}, 需求额度: {}", userId, amount);
            return false;
        }

        // 3. 记录 TCC 流水（绑定 txId 和 chatId）
        QuotaTransactionRecord record = new QuotaTransactionRecord();
        record.setTxId(txId);
        record.setChatId(chatId); // 持久化对话 ID，方便后续对账
        record.setUserId(userId);
        record.setAmount(amount);
        record.setStatus(0); // 设置状态为 TRY (0)
        record.setCreateTime(LocalDateTime.now()); // 手动补上
        record.setUpdateTime(LocalDateTime.now());

        try {
            quotaTransactionRecordService.save(record);
        } catch (Exception e) {
            // 极端并发下，如果两个相同的 txId 通过了第 1 步的检查
            // 数据库的 Primary Key 唯一约束会抛出异常，触发上方事务回滚，确保额度退回
            log.error("Try 阶段异常：流水插入冲突。txId: {}", txId, e);
            throw new RuntimeException("TCC 事务异常，触发自动回滚");
        }

        log.info("Try 阶段成功：额度已冻结并记录流水。txId: {}, chatId: {}", txId, chatId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmQuota(Long txId, Long userId) {

        // 1. 【核心大活：状态机前置】利用数据库行级锁+乐观锁，直接进行状态抢占
        // UPDATE record SET status = 1 WHERE txId = ? AND status = 0
        boolean recordUpdated = quotaTransactionRecordService.lambdaUpdate()
                .eq(QuotaTransactionRecord::getTxId, txId)
                .eq(QuotaTransactionRecord::getStatus, 0) // 必须是 TRY 状态
                .set(QuotaTransactionRecord::getStatus, 1) // 变更为 CONFIRM
                .set(QuotaTransactionRecord::getUpdateTime, LocalDateTime.now())
                .update();

        // 2. 判断抢占结果
        if (!recordUpdated) {
            // 如果抢占失败（更新行数为 0），说明出现了并发，或者状态不为 0。
            // 此时我们【再】去数据库查明死因，走防御和幂等逻辑。这种查询是轻量级的。
            QuotaTransactionRecord record = quotaTransactionRecordService.getById(txId);

            // 防御场景 A：记录压根没落库（发生了乱序）
            if (record == null) {
                log.error("Confirm 阶段异常：找不到对应的 Try 流水记录。txId: {}", txId);
                throw new RuntimeException("未查到 Try 记录，等待 MQ 重试");
            }

            // 幂等场景：已经被当前或其他线程 Confirm 过了
            if (record.getStatus() == 1) {
                log.info("Confirm 阶段幂等：该流水已成功确认过。txId: {}", txId);
                return true;
            }

            // 防御场景 B：已经被 Cancel 阶段截胡
            if (record.getStatus() == 2) {
                log.warn("Confirm 阶段拦截：该事务已被 Cancel 退款，放弃本次 Confirm 扣减。txId: {}", txId);
                return true;
            }

            // 理论上不可达，严谨起见兜底
            throw new RuntimeException("未知的流水状态异常");
        }

        // 3. 【执行扣减】
        // 走到这里，说明当前线程成功拿到了该 txId 从 0 到 1 的“变更权”。
        // 并发已经被彻底挡在门外，此时可以安心执行实际的资产扣减。
        QuotaTransactionRecord record = quotaTransactionRecordService.getById(txId);
        Integer amount = record.getAmount();

        boolean walletUpdated = lambdaUpdate()
                .eq(UserWallet::getId, userId)
                .ge(UserWallet::getFrozenQuota, amount) // 严谨的兜底判断
                .setSql("frozen_quota = frozen_quota - " + amount)
                .set(UserWallet::getUpdateTime, LocalDateTime.now())
                .update();

        if (!walletUpdated) {
            log.error("Confirm 阶段严重异常：冻结额度不足或用户不存在。userId: {}, txId: {}", userId, txId);
            // 这里抛出异常，整个 @Transactional 回滚。
            // 前面第一步抢占到的 recordUpdated = 1 也会被数据库回滚，恢复为 status = 0。
            throw new RuntimeException("扣除冻结额度失败，触发回滚");
        }

        log.info("Confirm 阶段成功：冻结额度已核销，流水状态已更新。txId: {}", txId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelQuota(Long txId, Long chatId, Long userId, Integer amount) {
        // 1. 查询流水记录
        QuotaTransactionRecord record = quotaTransactionRecordService.getById(txId);

        // 2. 【核心防御：空回滚与插墓碑】
        if (record == null) {
            log.warn("Cancel 阶段触发空回滚：未找到 Try 记录。立刻写入防悬挂墓碑，txId: {}", txId);

            try {
                killZombieChat(chatId,txId,userId);
            } catch (DuplicateKeyException e) {
                // 捕获唯一索引冲突：如果 Try 和 Cancel 在同一毫秒并发到达，触发冲突
                log.error("Cancel 阶段：墓碑插入失败，发生并发冲突。txId: {}", txId, e);
                throw new RuntimeException("防悬挂记录插入冲突，触发回滚");
            }
            return true; // 空回滚处理完毕，告知 MQ 消费成功
        }

        int currentStatus = record.getStatus();

        // 3. 幂等性检查
        if (currentStatus == 2) {
            log.info("Cancel 阶段幂等：该流水已成功回滚过。txId: {}", txId);
            return true;
        }

        // 4. 业务隔离防线：已被 Confirm
        if (currentStatus == 1) {
            log.error("Cancel 拦截：该事务已 Confirm，拒绝执行 Cancel 回滚！txId: {}", txId);
            return true;
        }

        // 5. 还原钱包额度 (增加 quota，扣除 frozen_quota)
        boolean walletUpdated = this.lambdaUpdate()
                .eq(UserWallet::getId, userId)
                .ge(UserWallet::getFrozenQuota, amount) // 防止并发扣成负数
                .setSql("quota = quota + " + amount)
                .setSql("frozen_quota = frozen_quota - " + amount)
                .set(UserWallet::getUpdateTime, LocalDateTime.now())
                .update();

        if (!walletUpdated) {
            log.error("Cancel 阶段异常：冻结额度不足或用户不存在。userId: {}, txId: {}", userId, txId);
            throw new RuntimeException("退还冻结额度失败，触发回滚");
        }

        // 6. 更新流水状态为 CANCEL (2)
        boolean recordUpdated = quotaTransactionRecordService.lambdaUpdate()
                .eq(QuotaTransactionRecord::getTxId, txId)
                .eq(QuotaTransactionRecord::getStatus, 0)
                .set(QuotaTransactionRecord::getStatus, 2)
                .set(QuotaTransactionRecord::getUpdateTime, LocalDateTime.now())
                .update();

        if (!recordUpdated) {
            log.error("Cancel 阶段流水更新失败，存在并发状态修改。txId: {}", txId);
            throw new RuntimeException("更新流水状态失败，触发回滚");
        }

        log.info("Cancel 阶段成功：额度已退还，流水更新为 CANCEL。txId: {}", txId);
        return true;
    }

}
