package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.protocol")
public class NoteGradingProtocolProperties {
    private String schemaVersion = "v1";
}
