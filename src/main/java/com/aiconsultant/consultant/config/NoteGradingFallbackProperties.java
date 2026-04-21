package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.fallback")
public class NoteGradingFallbackProperties {

    /** When false, pipeline errors go straight to the fixed user message. */
    private boolean enabled = true;

    /** Max chars read from Redis pool fields concatenated into summarizer context. */
    private int contextMaxChars = 6000;
}
