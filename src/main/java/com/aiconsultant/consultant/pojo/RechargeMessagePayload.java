package com.aiconsultant.consultant.pojo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * MQ 充值消息载荷 DTO
 * 用于在发件箱和消费者之间传递核心重试参数
 */
@Data
public class RechargeMessagePayload {

    /**
     * 充值用户的 ID
     */
    private Long userId;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 内部业务订单号 (用于消费端查重和对账)
     */
    private String orderNo;
}
