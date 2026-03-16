package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.UserWallet;

public interface QuotaService extends IService<UserWallet> {

    /**
     * 阶段一：Try (预占额度)
     * 在大模型开始生成回答前调用。
     * 将可用额度 (quota) 转移至冻结额度 (frozenQuota)，并写入状态为 0 的流水。
     *
     * @param userId 用户 ID
     * @param chatId 对话 ID (作为 TCC 的全局事务 ID，保证幂等性)
     * @param amount 预占额度数量 (通常为 1)
     * @return true: 预占成功；false: 余额不足或预占失败
     */
    boolean tryQuota(Long txId, Long chatId, Long userId, Integer amount);

    /**
     * 阶段二：Confirm (确认消耗)
     * 在大模型成功生成回答并落库后调用 (可通过 RabbitMQ 异步消费)。
     * 扣除冻结额度 (frozenQuota)，并将流水状态更新为 1。
     *
     * @param userId 用户 ID
     * @param txId 对话 ID
     * @return true: 确认成功；false: 确认失败
     */
    boolean confirmQuota(Long txId, Long userId);

    /**
     * 阶段三：Cancel (失败回滚/退还额度)
     * 在大模型生成失败、服务异常或超时后调用 (可通过 RabbitMQ 异步消费)。
     * 将冻结额度 (frozenQuota) 退还至可用额度 (quota)，并将流水状态更新为 2。
     * 需要处理“空回滚”和“防悬挂”逻辑。
     *
     * @param userId 用户 ID
     * @param chatId 对话 ID
     * @param amount 需要退还的额度数量
     * @return true: 回滚成功；false: 回滚失败
     */
    boolean cancelQuota(Long txId, Long chatId, Long userId, Integer amount);

}
