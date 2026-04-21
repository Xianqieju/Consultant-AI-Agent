package com.aiconsultant.consultant.notegrading;

/**
 * Redis key layout for the note-grading cross-step pool (Hash per pipeline).
 */
public final class NoteGradingRedisKeys {
    private NoteGradingRedisKeys() {}

    /**
     * @param sessionId optional; when non-null key is namespaced by session for support queries
     */
    public static String pool(Long sessionId, String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId required");
        }
        if (sessionId != null) {
            return "note:grading:pool:" + sessionId + ":" + pipelineId;
        }
        return "note:grading:pool:" + pipelineId;
    }

    public static String events(Long sessionId, String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("pipelineId required");
        }
        if (sessionId != null) {
            return "note:grading:events:" + sessionId + ":" + pipelineId;
        }
        return "note:grading:events:" + pipelineId;
    }
}
