package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class BankCallbackDTO {
    private String payToken;  // 银行凭证
    private String orderNo;   // 我们的订单号
    private Integer status;   // 银行扣款结果：1-成功，2-失败
}
