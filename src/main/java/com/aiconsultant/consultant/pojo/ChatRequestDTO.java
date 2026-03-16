package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String message;
    private Long agentId;
}