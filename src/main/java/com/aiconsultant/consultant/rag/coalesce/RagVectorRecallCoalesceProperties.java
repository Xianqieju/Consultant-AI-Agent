package com.aiconsultant.consultant.rag.coalesce;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 首轮向量召回：跨节点 Redis 缓存 + 分布式锁 + Topic 唤醒；进程内 CHM 合并见服务实现。
 */
@ConfigurationProperties(prefix = "app.rag.coalesce.vector")
public class RagVectorRecallCoalesceProperties {

    /**
     * 关闭时 {@link RagVectorRecallCoalesceService#recallMono} 仅透传 embed+search，不写 Redis。
     */
    private boolean enabled = false;
    /**
     * 与 {@code langchain4j.open-ai.embedding-model.model-name} 对齐，换模型后避免误读旧缓存。
     */
    private String embeddingModelId = "text-embedding-v3";
    private int ttlSeconds = 120;
    /** tryLock 租约（秒），与 ChatAgent 侧接近 */
    private int lockLeaseSeconds = 10;
    private int pubSubAwaitMs = 2000;
    private int maxLoadAttempts = 3;
    private long hedgeDelayMs = 1500;
    private int monoTimeoutSeconds = 30;
    private int maxCachedMatches = 100;
    private int maxCachedPayloadBytes = 1_500_000;
    /** 未抢到锁时等待 publish 后再重试 Redis */
    private int wakeJitterMinMs = 50;
    private int wakeJitterMaxMs = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public int getLockLeaseSeconds() {
        return lockLeaseSeconds;
    }

    public void setLockLeaseSeconds(int lockLeaseSeconds) {
        this.lockLeaseSeconds = lockLeaseSeconds;
    }

    public int getPubSubAwaitMs() {
        return pubSubAwaitMs;
    }

    public void setPubSubAwaitMs(int pubSubAwaitMs) {
        this.pubSubAwaitMs = pubSubAwaitMs;
    }

    public int getMaxLoadAttempts() {
        return maxLoadAttempts;
    }

    public void setMaxLoadAttempts(int maxLoadAttempts) {
        this.maxLoadAttempts = maxLoadAttempts;
    }

    public long getHedgeDelayMs() {
        return hedgeDelayMs;
    }

    public void setHedgeDelayMs(long hedgeDelayMs) {
        this.hedgeDelayMs = hedgeDelayMs;
    }

    public int getMonoTimeoutSeconds() {
        return monoTimeoutSeconds;
    }

    public void setMonoTimeoutSeconds(int monoTimeoutSeconds) {
        this.monoTimeoutSeconds = monoTimeoutSeconds;
    }

    public int getMaxCachedMatches() {
        return maxCachedMatches;
    }

    public void setMaxCachedMatches(int maxCachedMatches) {
        this.maxCachedMatches = maxCachedMatches;
    }

    public int getMaxCachedPayloadBytes() {
        return maxCachedPayloadBytes;
    }

    public void setMaxCachedPayloadBytes(int maxCachedPayloadBytes) {
        this.maxCachedPayloadBytes = maxCachedPayloadBytes;
    }

    public int getWakeJitterMinMs() {
        return wakeJitterMinMs;
    }

    public void setWakeJitterMinMs(int wakeJitterMinMs) {
        this.wakeJitterMinMs = wakeJitterMinMs;
    }

    public int getWakeJitterMaxMs() {
        return wakeJitterMaxMs;
    }

    public void setWakeJitterMaxMs(int wakeJitterMaxMs) {
        this.wakeJitterMaxMs = wakeJitterMaxMs;
    }
}
