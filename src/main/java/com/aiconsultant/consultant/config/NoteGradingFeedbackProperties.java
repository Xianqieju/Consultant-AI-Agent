package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.feedback")
public class NoteGradingFeedbackProperties {

    private boolean enabled = true;

    private int maxUserCommentLength = 2000;

    private int maxGradedExcerptLength = 4000;
}
