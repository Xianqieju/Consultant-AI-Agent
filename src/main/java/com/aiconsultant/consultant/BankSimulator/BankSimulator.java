package com.aiconsultant.consultant.BankSimulator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BankSimulator {

    /** Key: payToken */
    private static final ConcurrentHashMap<String, BankTransaction> bankLedger = new ConcurrentHashMap<>();
    /** Key: merchantOrderNo -> payToken */
    private static final ConcurrentHashMap<String, String> orderNoToPayToken = new ConcurrentHashMap<>();

    /**
     * 模拟向银行申请支付，换取鉴权码 (payToken)
     */
    public static String applyPayment(String merchantOrderNo, BigDecimal amount, String channel) {
        String payToken = "BANK_" + channel + "_" + UUID.randomUUID().toString().replace("-", "");

        BankTransaction transaction = new BankTransaction();
        transaction.setMerchantOrderNo(merchantOrderNo);
        transaction.setAmount(amount);
        transaction.setStatus(0); // 0: 待支付

        bankLedger.put(payToken, transaction);
        orderNoToPayToken.put(merchantOrderNo, payToken);
        log.info("[银行模拟器] 收到商户下单请求，订单号: {}, 分配鉴权码: {}", merchantOrderNo, payToken);

        return payToken;
    }

    /**
     * 商户回调成功时同步银行侧账本为已支付（真实场景中银行已扣款成功）。
     */
    public static void recordCallbackSuccess(String merchantOrderNo) {
        String payToken = orderNoToPayToken.get(merchantOrderNo);
        if (payToken == null) {
            log.warn("[银行模拟器] 回调成功但无订单映射 orderNo={}", merchantOrderNo);
            return;
        }
        BankTransaction tx = bankLedger.get(payToken);
        if (tx != null) {
            tx.setStatus(1);
            log.info("[银行模拟器] 回调确认支付成功 orderNo={}", merchantOrderNo);
        }
    }

    /**
     * 模拟「用户已在银行侧完成支付、银行账本已入账」但商户回调尚未到达（用于联调取消补偿）。
     */
    public static void simulateCustomerPaidAtBank(String merchantOrderNo) {
        String payToken = orderNoToPayToken.get(merchantOrderNo);
        if (payToken == null) {
            log.warn("[银行模拟器] simulateCustomerPaidAtBank 无订单 {}", merchantOrderNo);
            return;
        }
        BankTransaction tx = bankLedger.get(payToken);
        if (tx != null) {
            tx.setStatus(1);
            log.info("[银行模拟器] 模拟用户已在银行支付成功 orderNo={}", merchantOrderNo);
        }
    }

    /**
     * 取消任务调用的银行查询：返回银行侧支付状态。0 待支付，1 成功，2 失败；-1 无此单。
     */
    public static int queryPaymentStatusByOrderNo(String merchantOrderNo) {
        String payToken = orderNoToPayToken.get(merchantOrderNo);
        if (payToken == null) {
            return -1;
        }
        BankTransaction tx = bankLedger.get(payToken);
        if (tx == null) {
            return -1;
        }
        return tx.getStatus() == null ? 0 : tx.getStatus();
    }

    @Data
    public static class BankTransaction {
        private String merchantOrderNo;
        private BigDecimal amount;
        /** 0:待支付, 1:成功, 2:失败 */
        private Integer status;
    }
}
