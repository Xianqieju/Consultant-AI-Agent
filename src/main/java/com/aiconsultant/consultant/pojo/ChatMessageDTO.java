package com.aiconsultant.consultant.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO implements Serializable {
    private Long id;         // 预生成的对话消息 ID (chatId)
    private Long userId;
    private Long sessionId;
    private Integer role;
    private String content;
    private Long correlationId; // 关联流水事务 txId
    private Integer status;     // 状态：0-成功, 1-生成失败
}
