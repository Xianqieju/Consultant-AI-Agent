package com.aiconsultant.consultant.notegrading.protocol;

import lombok.Data;

@Data
public class NoteTaskEnvelope {
    private String schemaVersion;
    private String pipelineId;
    private String taskId;
    private AgentRole agentRole;
    private TaskStage stage;
    private int attempt;
    private long createdAt;
}
