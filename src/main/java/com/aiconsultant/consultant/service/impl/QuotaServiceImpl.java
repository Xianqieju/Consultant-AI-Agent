package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.QuotaTransactionRecord;
import com.aiconsultant.consultant.entity.UserWallet;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.service.QuotaService;
import com.aiconsultant.consultant.service.QuotaTransactionRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class QuotaServiceImpl extends ServiceImpl<UserWalletMapper, UserWallet> implements QuotaService {

    @Autowired
    private QuotaTransactionRecordService quotaTransactionRecordService;

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
        // 1. 查询流水记录
        QuotaTransactionRecord record = quotaTransactionRecordService.getById(txId);

        // 【防御场景 A】：Try 记录不存在
        if (record == null) {
            log.error("Confirm 阶段异常：找不到对应的 Try 流水记录。可能发生乱序或脏数据，txId: {}", txId);
            throw new RuntimeException("未查到 Try 记录，等待重试");
        }

        int currentStatus = record.getStatus();

        // 【防御场景 B】：已经被 Cancel
        if (currentStatus == 2) {
            log.warn("Confirm 阶段拦截：该事务已被 Cancel 退款，放弃本次 Confirm 扣减。业务防损触发，txId: {}", txId);
            return true; // 放弃处理，告知 MQ 消费结束
        }

        // 2. 幂等性检查：如果已经 Confirm 过，直接返回成功
        if (currentStatus == 1) {
            log.info("Confirm 阶段幂等：该流水已成功确认过。txId: {}", txId);
            return true;
        }

        // 此时 status 必定为 0 (TRY)
        Integer amount = record.getAmount();

        // 3. 扣除冻结额度 (Try 阶段已经把 quota 减掉了，这里只清算 frozen_quota)
        // 利用条件语句 ge(frozen_quota, amount) 防止并发导致冻结额度扣成负数
        boolean walletUpdated = lambdaUpdate()
                .eq(UserWallet::getId, userId)
                .ge(UserWallet::getFrozenQuota, amount)
                .setSql("frozen_quota = frozen_quota - " + amount)
                .set(UserWallet::getUpdateTime, LocalDateTime.now())
                .update();

        if (!walletUpdated) {
            log.error("Confirm 阶段严重异常：冻结额度不足或用户不存在。userId: {}, txId: {}", userId, txId);
            // 抛出异常，触发事务回滚，确保流水表状态也不会被修改
            throw new RuntimeException("扣除冻结额度失败，触发回滚");
        }

        // 4. 更新流水状态为 CONFIRM (1)
        // 结合状态机乐观锁：eq(status, 0) 确保只有在 TRY 状态下才能变为 CONFIRM
        boolean recordUpdated = quotaTransactionRecordService.lambdaUpdate()
                .eq(QuotaTransactionRecord::getTxId, txId)
                .eq(QuotaTransactionRecord::getStatus, 0)
                .set(QuotaTransactionRecord::getStatus, 1)
                .set(QuotaTransactionRecord::getUpdateTime, LocalDateTime.now())
                .update();

        if (!recordUpdated) {
            log.error("Confirm 阶段流水更新失败，可能存在并发状态修改。txId: {}", txId);
            throw new RuntimeException("更新流水状态失败，触发回滚");
        }

        log.info("Confirm 阶段成功：冻结额度已核销，流水状态已更新。txId: {}", txId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelQuota(Long txId, Long chatId, Long userId, Integer amount) {
        // 1. 查询流水记录
        QuotaTransactionRecord record = quotaTransactionRecordService.getById(txId);

        // 2. 【核心防御：空回滚与防悬挂】
        if (record == null) {
            log.warn("Cancel 阶段触发空回滚：未找到 Try 记录。写入防悬挂标记，txId: {}", txId);

            // 创建一条状态为 2 (CANCEL) 的记录
            // 这样即使迟到的 Try 请求随后到达，也会因为发现 status=2 而被拦截，从而实现“防悬挂”
            QuotaTransactionRecord dummyRecord = new QuotaTransactionRecord();
            dummyRecord.setTxId(txId);
            dummyRecord.setChatId(chatId);
            dummyRecord.setUserId(userId);
            dummyRecord.setAmount(amount);
            dummyRecord.setStatus(2);

            try {
                quotaTransactionRecordService.save(dummyRecord);
            } catch (Exception e) {
                // 捕获唯一索引冲突：如果 Try 和 Cancel 在同一毫秒并发到达，可能触发冲突
                log.error("Cancel 阶段：防悬挂记录插入并发冲突。txId: {}", txId, e);
                throw new RuntimeException("防悬挂记录插入失败，触发事务回滚");
            }
            return true; // 空回滚处理完毕，告知 MQ 消费成功
        }

        int currentStatus = record.getStatus();

        // 3. 幂等性检查：如果已经 Cancel 过，直接返回成功
        if (currentStatus == 2) {
            log.info("Cancel 阶段幂等：该流水已成功回滚过。txId: {}", txId);
            return true;
        }

        // 4. 【业务隔离防线】：已被 Confirm
        if (currentStatus == 1) {
            log.error("Cancel 阶段严重拦截：该事务已 Confirm 完成扣款，拒绝执行 Cancel 回滚！需人工介入对账，txId: {}", txId);
            // 返回 true 告知 MQ 停止重试，因为状态 1 是不可逆的终态
            return true;
        }

        // 此时 status 必定为 0 (TRY)，执行正式退还逻辑

        // 5. 还原钱包额度 (增加 quota，扣除 frozen_quota)
        boolean walletUpdated = this.lambdaUpdate()
                .eq(UserWallet::getId, userId)
                // 确保有足够的冻结额度可供退还，防止并发下扣成负数
                .ge(UserWallet::getFrozenQuota, amount)
                .setSql("quota = quota + " + amount)
                .setSql("frozen_quota = frozen_quota - " + amount)
                .set(UserWallet::getUpdateTime, LocalDateTime.now())
                .update();

        if (!walletUpdated) {
            log.error("Cancel 阶段异常：冻结额度不足或用户不存在，无法退还。userId: {}, txId: {}", userId, txId);
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

        log.info("Cancel 阶段成功：额度已退还，流水状态更新为 CANCEL。txId: {}", txId);
        return true;
    }

}
