package com.aiconsultant.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeRequestDTO {
    // 只需要传金额，userId 从 UserHolder 中拿，防篡改
    private BigDecimal amount;
    private String payChannel; // 例如: "WECHAT", "ALIPAY"
}
