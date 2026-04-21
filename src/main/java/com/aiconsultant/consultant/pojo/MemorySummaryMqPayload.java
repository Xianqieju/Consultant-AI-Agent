package com.aiconsultant.consultant.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemorySummaryMqPayload {
    private Long outboxId;
    private Long sessionId;
    private Long userId;
    private String taskType;
}
