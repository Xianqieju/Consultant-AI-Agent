package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.pojo.QuotaOperationDTO;
import com.aiconsultant.consultant.service.ChatMessageService;
import com.aiconsultant.consultant.service.QuotaService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class QuotaMessageListener {

    @Autowired
    private QuotaService quotaService;
    @Autowired
    ChatMessageService chatMessageService;

    private void updateMessageStatusToFailed(Long txId) {
        chatMessageService.lambdaUpdate()
                .eq(ChatMessage::getCorrelationId, txId)
                .set(ChatMessage::getStatus, 1) // 1 代表生成失败
                .update();
    }

    /**
     * 第一部分：监听 Confirm 消息
     * 触发时机：大模型成功生成回复并落库后
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "quota.confirm.queue", durable = "true"),
            exchange = @Exchange(name = "quota.direct", type = ExchangeTypes.DIRECT),
            key = "confirm"
    ))
    public void handleConfirmMessage(QuotaOperationDTO dto, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("MQ 接收到 Confirm 消息开始消费，txId: {}", dto.getTxId());
        try {
            // 执行确认扣减逻辑
            boolean result = quotaService.confirmQuota(dto.getTxId(), dto.getUserId());

            // 无论业务返回 true 还是 false (例如被 Cancel 拦截)，都代表当前消息处理完毕，应当 ACK
            channel.basicAck(deliveryTag, false);
            log.info("MQ Confirm 消息处理完毕，txId: {}", dto.getTxId());

        } catch (Exception e) {
            log.error("MQ Confirm 消息消费异常，txId: {}", dto.getTxId(), e);
            // 发生运行时异常 (例如底层数据库超时、乐观锁冲突、或我们主动抛出的 Try 未找到异常)
            // 拒绝消息并要求重回队列 (requeue = true)，等待下一次重试
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 第二部分：监听 Cancel 消息
     * 触发时机：大模型生成失败、超时或落库异常时
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "quota.cancel.queue", durable = "true"),
            exchange = @Exchange(name = "quota.direct", type = ExchangeTypes.DIRECT),
            key = "cancel"
    ))
    public void handleCancelMessage(QuotaOperationDTO dto, Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("MQ 接收到 Cancel 消息开始消费，txId: {}", dto.getTxId());
        try {
            // 执行退还额度及防悬挂逻辑
            boolean result = quotaService.cancelQuota(dto.getTxId(), dto.getChatId(), dto.getUserId(), dto.getAmount());
            updateMessageStatusToFailed(dto.getTxId());
            channel.basicAck(deliveryTag, false);
            log.info("MQ Cancel 消息处理完毕，txId: {}", dto.getTxId());

        } catch (Exception e) {
            log.error("MQ Cancel 消息消费异常，txId: {}", dto.getTxId(), e);
            // 回滚异常，重回队列重试
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
