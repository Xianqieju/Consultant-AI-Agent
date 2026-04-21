package com.aiconsultant.consultant.notegrading;

import com.aiconsultant.consultant.config.NoteGradingRetryProperties;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CancellationException;

/**
 * Builds a shared {@link Retry} spec for note-grading LLM steps (exponential backoff).
 */
public final class NoteGradingReactiveRetry {
    private NoteGradingReactiveRetry() {}

    /**
     * @return null when retries are disabled (caller must not attach {@code retryWhen})
     */
    public static Retry retrySpec(NoteGradingRetryProperties props) {
        if (props == null || !props.isEnabled() || props.getMaxAttemptsAfterFailure() <= 0) {
            return null;
        }
        long maxBackoff = Math.max(props.getInitialBackoffMs(), props.getMaxBackoffMs());
        return Retry.backoff(props.getMaxAttemptsAfterFailure(), Duration.ofMillis(props.getInitialBackoffMs()))
                .maxBackoff(Duration.ofMillis(maxBackoff))
                .filter(NoteGradingReactiveRetry::isRetriable)
                .jitter(props.getJitter())
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure());
    }

    private static boolean isRetriable(Throwable t) {
        if (t instanceof CancellationException) {
            return false;
        }
        if (t instanceof InterruptedException) {
            return false;
        }
        Throwable c = t;
        while (c != null) {
            if (c instanceof InterruptedException) {
                return false;
            }
            c = c.getCause();
        }
        return true;
    }
}
