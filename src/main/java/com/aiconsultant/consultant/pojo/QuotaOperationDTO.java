package com.aiconsultant.consultant.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuotaOperationDTO implements Serializable {
    private Long txId;     // 流水/事务 ID
    private Long chatId;   // 对话 ID
    private Long userId;   // 用户 ID
    private Integer amount; // 扣减额度
}
