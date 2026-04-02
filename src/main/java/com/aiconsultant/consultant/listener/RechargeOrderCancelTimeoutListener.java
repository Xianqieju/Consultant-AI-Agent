package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.BankSimulator.BankSimulator;
import com.aiconsultant.consultant.config.RabbitRechargeOrderDelayConfig;
import com.aiconsultant.consultant.entity.RechargeOrder;
import com.aiconsultant.consultant.pojo.BankCallbackDTO;
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
 * 订单创建后 15 分钟 TTL 到期进入本队列：查询银行；若银行已支付而本地仍待支付，则走与回调一致的事务+发件箱补偿。
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

            RechargeOrder order = rechargeOrderService.lambdaQuery()
                    .eq(RechargeOrder::getOrderNo, orderNo)
                    .one();
            if (order == null) {
                log.warn("取消下游订单不存在 orderNo={}", orderNo);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (order.getStatus() != null && order.getStatus() != 0) {
                log.info("取消下游跳过：订单已终态 orderNo={}, status={}", orderNo, order.getStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            int bankStatus = BankSimulator.queryPaymentStatusByOrderNo(orderNo);
            if (bankStatus != 1) {
                log.info("取消下游：银行侧未支付或查无此单 orderNo={}, bankStatus={}", orderNo, bankStatus);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.warn("取消下游补偿：银行已支付本地仍待支付，触发与回调一致的处理 orderNo={}", orderNo);
            BankCallbackDTO dto = new BankCallbackDTO();
            dto.setOrderNo(orderNo);
            dto.setStatus(1);
            dto.setPayToken("");
            String result = rechargeOrderService.handleBankCallback(dto);
            if ("SUCCESS".equals(result)) {
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
