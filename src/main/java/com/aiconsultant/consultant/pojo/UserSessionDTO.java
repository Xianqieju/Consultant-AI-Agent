package com.aiconsultant.consultant.pojo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserSessionDTO implements Serializable {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long userId;
    private Long agentId;
    private String title;
    private LocalDateTime updateTime; // 传给前端用于展示最近对话时间
}