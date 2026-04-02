package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("local_message")
public class LocalMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String exchange;
    private String routingKey;
    private String payload;
    private Integer status; // 0:NEW, 1:PUBLISHED, 2:FAILED
    private Integer retryCount;
}
