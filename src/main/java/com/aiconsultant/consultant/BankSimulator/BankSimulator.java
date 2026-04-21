package com.aiconsultant.consultant.BankSimulator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 银行侧模拟：账本状态见 {@link BankTransaction#getStatus()}
 * 0 待支付 1 已支付 2 失败 3 关单(未支付关闭) 4 已退款
 */
@Slf4j
public class BankSimulator {

    private static final ConcurrentHashMap<String, BankTransaction> bankLedger = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> orderNoToPayToken = new ConcurrentHashMap<>();

    public static String applyPayment(String merchantOrderNo, BigDecimal amount, String channel) {
        String payToken = "BANK_" + channel + "_" + UUID.randomUUID().toString().replace("-", "");

        BankTransaction transaction = new BankTransaction();
        transaction.setMerchantOrderNo(merchantOrderNo);
        transaction.setAmount(amount);
        transaction.setStatus(0);

        bankLedger.put(payToken, transaction);
        orderNoToPayToken.put(merchantOrderNo, payToken);
        log.info("[银行模拟器] 收到商户下单请求，订单号: {}, 分配鉴权码: {}", merchantOrderNo, payToken);

        return payToken;
    }

    private static BankTransaction requireTx(String merchantOrderNo) {
        String payToken = orderNoToPayToken.get(merchantOrderNo);
        if (payToken == null) {
            return null;
        }
        return bankLedger.get(payToken);
    }

    /**
     * 商户回调成功时同步银行侧为已支付。
     */
    public static void recordCallbackSuccess(String merchantOrderNo) {
        BankTransaction tx = requireTx(merchantOrderNo);
        if (tx != null && Integer.valueOf(0).equals(tx.getStatus())) {
            tx.setStatus(1);
            log.info("[银行模拟器] 回调确认支付成功 orderNo={}", merchantOrderNo);
        }
    }

    public static void simulateCustomerPaidAtBank(String merchantOrderNo) {
        BankTransaction tx = requireTx(merchantOrderNo);
        if (tx != null) {
            tx.setStatus(1);
            log.info("[银行模拟器] 模拟用户已在银行支付成功 orderNo={}", merchantOrderNo);
        }
    }

    /**
     * 关单：仅未支付单可关；已支付需先 {@link #simulateRefund(String)}。
     *
     * @return 是否关单成功
     */
    public static boolean simulateCloseOrder(String merchantOrderNo) {
        BankTransaction tx = requireTx(merchantOrderNo);
        if (tx == null) {
            log.warn("[银行模拟器] 关单失败，无此单 orderNo={}", merchantOrderNo);
            return false;
        }
        if (tx.getStatus() != null && tx.getStatus() == 1) {
            log.warn("[银行模拟器] 关单拒绝：已支付 orderNo={}", merchantOrderNo);
            return false;
        }
        if (tx.getStatus() != null && (tx.getStatus() == 3 || tx.getStatus() == 4)) {
            log.info("[银行模拟器] 关单幂等：已关单或已退款 orderNo={}", merchantOrderNo);
            return true;
        }
        tx.setStatus(3);
        log.info("[银行模拟器] 关单成功 orderNo={}", merchantOrderNo);
        return true;
    }

    /**
     * 模拟退款：仅对已支付流水退款一次。
     *
     * @return 是否退款成功
     */
    public static boolean simulateRefund(String merchantOrderNo) {
        BankTransaction tx = requireTx(merchantOrderNo);
        if (tx == null) {
            log.warn("[银行模拟器] 退款失败，无此单 orderNo={}", merchantOrderNo);
            return false;
        }
        if (tx.getStatus() == null || tx.getStatus() != 1) {
            log.warn("[银行模拟器] 退款拒绝：当前非已支付状态 orderNo={}, status={}", merchantOrderNo, tx.getStatus());
            return false;
        }
        tx.setStatus(4);
        log.info("[银行模拟器] 退款成功 orderNo={}, amount={}", merchantOrderNo, tx.getAmount());
        return true;
    }

    /**
     * 查询银行侧状态：-1 无单；否则 0/1/2/3/4
     */
    public static int queryPaymentStatusByOrderNo(String merchantOrderNo) {
        BankTransaction tx = requireTx(merchantOrderNo);
        if (tx == null) {
            return -1;
        }
        return tx.getStatus() == null ? 0 : tx.getStatus();
    }

    @Data
    public static class BankTransaction {
        private String merchantOrderNo;
        private BigDecimal amount;
        /** 0 待支付 1 已支付 2 失败 3 关单 4 已退款 */
        private Integer status;
    }
}
