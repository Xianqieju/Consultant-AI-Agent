package com.aiconsultant.consultant.notegrading.audit;

import lombok.Data;

@Data
public class NoteAuditEvent {
    private String eventType;
    private String stage;
    private String taskId;
    private String agentRole;
    private int attempt;
    private String status;
    private long durationMs;
    private String error;
    private String payloadRef;
    private long ts;
}
