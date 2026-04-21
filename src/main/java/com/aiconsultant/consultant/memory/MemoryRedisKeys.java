package com.aiconsultant.consultant.memory;

public final class MemoryRedisKeys {
    private MemoryRedisKeys() {}

    public static String summaryCache(Long sessionId) {
        return "memory:summary:" + sessionId;
    }

    public static String profileCache(Long userId, Long sessionId) {
        return "memory:profile:" + userId + ":" + sessionId;
    }

    /** 仅 sessionId 时读画像（与 ChatMemory 的 memoryId 对齐） */
    public static String profileSessionCache(Long sessionId) {
        return "memory:profile:session:" + sessionId;
    }

    public static String globalProfileCache(Long userId) {
        return "memory:profile:user:" + userId;
    }
}
