package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.entity.RechargeOrder;
import com.aiconsultant.consultant.mapper.RechargeOrderMapper;
import com.aiconsultant.consultant.pojo.BankCallbackDTO;
import com.aiconsultant.consultant.pojo.RechargeRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RechargeOrderService extends IService<RechargeOrder> {
    Result apply(RechargeRequestDTO dto);

    String handleBankCallback(BankCallbackDTO callbackDto);

    /** 商户关单（仅待支付），乐观锁更新 + 银行关单模拟 */
    Result closeOrder(String orderNo);

    /** 银行模拟：对已支付流水退款（联调/补偿用） */
    Result simulateBankRefund(String orderNo);

    /**
     * 与 MQ 超时取消下游一致：查银行、补偿回调或关单。
     *
     * @return true 表示 MQ 可 ACK（含已终态、无需处理）；false 表示应 NACK 重试（如回调返回 FAIL）
     */
    boolean reconcileTimeoutOrder(String orderNo);
}
