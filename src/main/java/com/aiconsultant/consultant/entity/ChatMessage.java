package com.aiconsultant.consultant.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.INPUT)
    private Long id;             // 消息ID（MyBatis-Plus 默认使用雪花算法生成）

    private Long userId;         // 用户ID

    private Long sessionId;    // 对话ID（暂未实装，但保留字段，可直接存入 memoryId）

    private Integer role;        // 发送方角色：0-User（用户提问）, 1-Assistant（AI回复）

    private String content;      // 对话内容

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime; // 存入时间戳
    private Long correlationId; // 存入 txId
    private Integer status;     // 0-成功, 1-生成失败
}