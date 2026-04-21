package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.retry")
public class NoteGradingRetryProperties {

    /** When false, LLM steps are not retried on failure. */
    private boolean enabled = true;

    /**
     * Number of retry attempts after the first failure (Reactor semantics).
     * Set to 2 for at most 3 total LLM invocations per step.
     */
    private int maxAttemptsAfterFailure = 2;

    private long initialBackoffMs = 500L;

    private long maxBackoffMs = 8000L;

    /** Jitter factor 0..1 for backoff (e.g. 0.5). */
    private double jitter = 0.5d;
}
