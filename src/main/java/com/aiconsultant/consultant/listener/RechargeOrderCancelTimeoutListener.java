package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.config.RabbitRechargeOrderDelayConfig;
import com.aiconsultant.consultant.service.RechargeOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单创建后 15 分钟 TTL 到期进入本队列：查询银行；
 * 若银行已支付而本地仍待支付则补偿回调；否则乐观锁关单并同步银行关单模拟。
 */
@Slf4j
@Component
public class RechargeOrderCancelTimeoutListener {

    @Autowired
    private RechargeOrderService rechargeOrderService;
    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitRechargeOrderDelayConfig.ORDER_CANCEL_QUEUE, ackMode = "MANUAL")
    public void onOrderCancelTimeout(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String body = new String(message.getBody());
            JsonNode node = objectMapper.readTree(body);
            String orderNo = node.has("orderNo") ? node.get("orderNo").asText() : null;
            if (orderNo == null || orderNo.isEmpty()) {
                log.warn("取消下游收到无效消息: {}", body);
                channel.basicAck(deliveryTag, false);
                return;
            }

            boolean ack = rechargeOrderService.reconcileTimeoutOrder(orderNo);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (Exception e) {
            log.error("取消下游处理异常", e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
