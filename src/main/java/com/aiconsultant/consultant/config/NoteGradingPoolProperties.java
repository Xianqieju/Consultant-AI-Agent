package com.aiconsultant.consultant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.note-grading.pool")
public class NoteGradingPoolProperties {

    /** When false, no Redis pool I/O (default for backward compatibility). */
    private boolean enabled = false;

    /** TTL for the pool Hash after creation (hours). */
    private int ttlHours = 24;

    /** When true, store full user note in the Hash under {@code fullNote}. */
    private boolean storeFullNote = false;

    /**
     * After successful aggregator output, shorten remaining TTL to this many hours.
     * When 0, keep the original {@link #ttlHours} until natural expiry.
     */
    private int afterSuccessTtlHours = 1;
}
