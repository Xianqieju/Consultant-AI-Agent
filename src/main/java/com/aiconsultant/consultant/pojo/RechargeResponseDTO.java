package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class RechargeResponseDTO {
    private String orderNo;   // 内部订单号
    private String payToken;  // 银行给的“鉴权码”，前端用它去跳转或弹窗
}
