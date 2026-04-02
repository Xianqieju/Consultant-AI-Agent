package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.config.RabbitRechargeConfig;
import com.aiconsultant.consultant.entity.MqIdempotent;
import com.aiconsultant.consultant.mapper.MqIdempotentMapper;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.pojo.RechargeMessagePayload;
import com.aiconsultant.consultant.service.UserWalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.aiconsultant.consultant.entity.UserWallet;
import java.io.IOException;

@Slf4j
@Component
public class RechargeWalletListener {

    @Autowired
    private MqIdempotentMapper mqIdempotentMapper;
    @Autowired
    private UserWalletService userWalletService;
    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitRechargeConfig.RECHARGE_QUEUE, ackMode = "MANUAL")
    @Transactional(rollbackFor = Exception.class)
    public void onRechargeMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payloadJson = new String(message.getBody());
        RechargeMessagePayload payload = null;

        try {
            payload = objectMapper.readValue(payloadJson, RechargeMessagePayload.class);
            String orderNo = payload.getOrderNo();

            // 1. 尝试插入本地事务幂等表
            MqIdempotent idempotentRecord = new MqIdempotent();
            idempotentRecord.setBizId(orderNo);
            idempotentRecord.setConsumerName("wallet_add_funds");

            mqIdempotentMapper.insert(idempotentRecord);
            // 若执行到此未抛出异常，说明是首次消费

            // 2. 执行真正的业务逻辑：加款
            // UPDATE user_wallet SET quota = quota + #{amount} WHERE user_id = #{userId}
            userWalletService.lambdaUpdate()
                    // 根据你之前的注册逻辑，UserWallet 的 id 就是 userId
                    .eq(UserWallet::getId, payload.getUserId())
                    // 使用 setSql 直接在数据库层进行原子累加，避免先查后写的并发覆盖问题
                    .setSql("quota = quota + " + payload.getAmount())
                    .update();

            // 3. 业务与幂等表同事务提交，手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("订单加款消费成功, orderNo: {}", orderNo);

        } catch (DuplicateKeyException e) {
            // 命中唯一键约束，说明已成功消费过。视为正常处理完成，执行 ACK，丢弃重复消息
            log.info("触发防重消费，直接 ACK。orderNo: {}", payload != null ? payload.getOrderNo() : "未知");
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 业务异常或数据库异常。拒绝消息并抛出，由 Spring AMQP 的 Retry 机制接管
            // 达到重试上限后，消息会被路由至死信交换机
            log.error("订单加款消费异常，触发重试", e);
            channel.basicNack(deliveryTag, false, false);
            throw new RuntimeException("消费异常，要求重试", e);
        }
    }
}
