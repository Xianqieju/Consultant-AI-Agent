package com.aiconsultant.consultant.service;

public interface MemorySummaryCacheService {

    String getCachedSummaryText(Long sessionId);

    void putSummaryText(Long sessionId, String text);

    void invalidateSummary(Long sessionId);

    String getCachedProfileJson(Long userId, Long sessionId);

    void putProfileJson(Long userId, Long sessionId, String json);

    void invalidateProfile(Long userId, Long sessionId);

    String getCachedProfileJsonBySession(Long sessionId);

    void putProfileJsonBySession(Long sessionId, String json);

    void invalidateProfileSession(Long sessionId);

    String getCachedGlobalProfileJson(Long userId);

    void putGlobalProfileJson(Long userId, String json);

    void invalidateGlobalProfile(Long userId);
}
