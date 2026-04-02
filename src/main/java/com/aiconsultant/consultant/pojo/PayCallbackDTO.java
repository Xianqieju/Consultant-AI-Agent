package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class PayCallbackDTO {
    // 模拟第三方支付平台的回调对象
    private String orderNo;
    private Integer payStatus; // 1: 成功
    private String transactionId; // 第三方平台流水号
}
