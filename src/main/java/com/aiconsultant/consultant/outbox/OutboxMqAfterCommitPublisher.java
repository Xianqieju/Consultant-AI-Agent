package com.aiconsultant.consultant.outbox;

import com.aiconsultant.consultant.entity.LocalMessage;
import com.aiconsultant.consultant.mapper.LocalMessageMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 发件箱：本地消息入库与 MQ 投递在同一事务中提交；仅在事务成功 commit 后投递 MQ 并更新消息状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMqAfterCommitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final LocalMessageMapper localMessageMapper;

    /**
     * 注册当前事务在 commit 之后将 payload 投递至 MQ，并将 local_message 标记为已发送。
     */
    public void registerPublishAfterCommit(String messageId, String exchange, String routingKey, String payloadJson) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("无活跃事务，直接投递 MQ（非发件箱模式）messageId={}", messageId);
            rabbitTemplate.convertAndSend(exchange, routingKey, payloadJson);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(exchange, routingKey, payloadJson);
                    localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                            .eq(LocalMessage::getMessageId, messageId)
                            .set(LocalMessage::getStatus, 1));
                    log.info("发件箱投递成功 messageId={}, exchange={}, routingKey={}", messageId, exchange, routingKey);
                } catch (Exception e) {
                    log.error("发件箱事务提交后投递失败 messageId={}", messageId, e);
                    localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                            .eq(LocalMessage::getMessageId, messageId)
                            .set(LocalMessage::getStatus, 2));
                }
            }
        });
    }
}
