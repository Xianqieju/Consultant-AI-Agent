package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.BankSimulator.BankSimulator;
import com.aiconsultant.consultant.config.RabbitRechargeOrderDelayConfig;
import com.aiconsultant.consultant.entity.LocalMessage;
import com.aiconsultant.consultant.entity.RechargeOrder;
import com.aiconsultant.consultant.mapper.LocalMessageMapper;
import com.aiconsultant.consultant.mapper.RechargeOrderMapper;
import com.aiconsultant.consultant.outbox.OutboxMqAfterCommitPublisher;
import com.aiconsultant.consultant.pojo.*;
import com.aiconsultant.consultant.service.RechargeOrderService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.aiconsultant.consultant.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RechargeOrderServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder> implements RechargeOrderService {

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;
    @Autowired
    private LocalMessageMapper localMessageMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxMqAfterCommitPublisher outboxMqAfterCommitPublisher;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result apply(RechargeRequestDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.fail("充值金额必须大于0");
        }
        if (dto.getPayChannel() == null || dto.getPayChannel().trim().isEmpty()) {
            return Result.fail("支付渠道不能为空");
        }

        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null || userDTO.getId() == null) {
            return Result.fail("未获取到登录状态");
        }
        Long userId = userDTO.getId();

        String orderNo = "RCG" + snowflakeIdWorker.nextId();

        RechargeOrder order = new RechargeOrder();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(dto.getAmount());
        order.setStatus(0);

        boolean saved = save(order);
        if (!saved) {
            log.error("充值订单创建失败, userId: {}, amount: {}", userId, dto.getAmount());
            return Result.fail("系统繁忙，创建订单失败");
        }

        String payToken = BankSimulator.applyPayment(orderNo, dto.getAmount(), dto.getPayChannel());

        RechargeResponseDTO responseDTO = new RechargeResponseDTO();
        responseDTO.setOrderNo(orderNo);
        responseDTO.setPayToken(payToken);

        final String orderNoFinal = orderNo;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Map<String, String> delayMsg = new HashMap<>();
                    delayMsg.put("orderNo", orderNoFinal);
                    String json = objectMapper.writeValueAsString(delayMsg);
                    rabbitTemplate.convertAndSend("", RabbitRechargeOrderDelayConfig.ORDER_TTL_QUEUE, json);
                    log.info("已投递订单超时取消延时消息 orderNo={}", orderNoFinal);
                } catch (Exception e) {
                    log.error("投递订单 TTL 队列失败 orderNo={}", orderNoFinal, e);
                }
            }
        });

        log.info("用户发起充值成功, userId: {}, orderNo: {}", userId, orderNo);
        return Result.ok(responseDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleBankCallback(BankCallbackDTO callbackDto) {
        String orderNo = callbackDto.getOrderNo();

        RechargeOrder order = this.getOne(new LambdaQueryWrapper<RechargeOrder>()
                .eq(RechargeOrder::getOrderNo, orderNo));

        if (order == null) {
            log.warn("收到回调但订单不存在，触发银行重试机制。orderNo: {}", orderNo);
            return "FAIL";
        }

        if (order.getStatus() != null && order.getStatus() == 1) {
            log.info("订单已支付，幂等直接返回成功。orderNo: {}", orderNo);
            return "SUCCESS";
        }

        if (callbackDto.getStatus() != null && callbackDto.getStatus() != 1) {
            order.setStatus(2);
            this.updateById(order);
            return "SUCCESS";
        }

        order.setStatus(1);
        this.updateById(order);
        BankSimulator.recordCallbackSuccess(orderNo);

        try {
            RechargeMessagePayload payloadObj = new RechargeMessagePayload();
            payloadObj.setUserId(order.getUserId());
            payloadObj.setAmount(order.getAmount());
            payloadObj.setOrderNo(orderNo);
            String payloadJson = objectMapper.writeValueAsString(payloadObj);

            LocalMessage message = new LocalMessage();
            message.setMessageId(orderNo);
            message.setExchange("recharge.exchange");
            message.setRoutingKey("recharge.success");
            message.setPayload(payloadJson);
            message.setStatus(0);
            message.setRetryCount(0);

            localMessageMapper.insert(message);
            outboxMqAfterCommitPublisher.registerPublishAfterCommit(
                    orderNo, message.getExchange(), message.getRoutingKey(), payloadJson);
        } catch (DuplicateKeyException e) {
            log.warn("本地消息表重复写入，补发发件箱投递。orderNo: {}", orderNo);
            try {
                RechargeMessagePayload payloadObj = new RechargeMessagePayload();
                payloadObj.setUserId(order.getUserId());
                payloadObj.setAmount(order.getAmount());
                payloadObj.setOrderNo(orderNo);
                String payloadJson = objectMapper.writeValueAsString(payloadObj);
                outboxMqAfterCommitPublisher.registerPublishAfterCommit(
                        orderNo, "recharge.exchange", "recharge.success", payloadJson);
            } catch (Exception ex) {
                log.error("补发发件箱失败 orderNo={}", orderNo, ex);
                throw new RuntimeException(ex);
            }
        } catch (Exception e) {
            log.error("本地消息构造或入库失败", e);
            throw new RuntimeException("本地消息入库失败", e);
        }

        return "SUCCESS";
    }
}
