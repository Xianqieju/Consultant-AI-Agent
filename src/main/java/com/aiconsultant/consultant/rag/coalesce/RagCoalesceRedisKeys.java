package com.aiconsultant.consultant.rag.coalesce;

/**
 * Redis / Redisson key 前缀；与 {@link RagVectorFlightKey#redisHashSuffix()} 组合后一 query 一 topic，避免互相唤醒。
 */
public final class RagCoalesceRedisKeys {

    public static final String CACHE_PREFIX = "rag:vec:cache:";
    public static final String LOCK_PREFIX = "rag:vec:lock:";
    public static final String TOPIC_PREFIX = "rag:vec:topic:";

    private RagCoalesceRedisKeys() {
    }

    public static String cacheKey(String suffix) {
        return CACHE_PREFIX + suffix;
    }

    public static String lockKey(String suffix) {
        return LOCK_PREFIX + suffix;
    }

    public static String topicKey(String suffix) {
        return TOPIC_PREFIX + suffix;
    }
}
