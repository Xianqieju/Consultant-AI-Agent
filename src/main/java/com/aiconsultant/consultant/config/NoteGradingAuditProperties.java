package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.audit")
public class NoteGradingAuditProperties {
    private boolean enabled = false;
    private int eventsTtlHours = 24;
    private String payloadMode = "summary";
}
