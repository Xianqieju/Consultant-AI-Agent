package com.aiconsultant.consultant.rag.coalesce;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 首轮向量召回合并：进程内 CHM + Reactor；跨节点 Redis 缓存 + 抢锁 + DB/向量查询 + Topic 唤醒。
 * 与 {@link com.aiconsultant.consultant.service.impl.ChatAgentServiceImpl} 模式对齐，互不共用代码。
 */
@Slf4j
@Service
public class RagVectorRecallCoalesceService {

    private static final String WAKE_MSG = "RAG_VEC_WAKE";

    private final RagVectorRecallCoalesceProperties properties;
    private final RagVectorSearchResultCodec codec;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> redisEmbeddingStore;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private final ConcurrentHashMap<RagVectorFlightKey, Mono<EmbeddingSearchResult<TextSegment>>> inflight =
            new ConcurrentHashMap<>();

    private final Executor cacheExecutor = Schedulers.boundedElastic()::schedule;
    private final ScheduledExecutorService hedgeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rag-vector-hedge");
        t.setDaemon(true);
        return t;
    });

    public RagVectorRecallCoalesceService(
            RagVectorRecallCoalesceProperties properties,
            RagVectorSearchResultCodec codec,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> redisEmbeddingStore,
            StringRedisTemplate stringRedisTemplate,
            RedissonClient redissonClient
    ) {
        this.properties = properties;
        this.codec = codec;
        this.embeddingModel = embeddingModel;
        this.redisEmbeddingStore = redisEmbeddingStore;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    /**
     * 合并语义：同 {@link RagVectorFlightKey} 的并发调用共享一次加载；关闭开关时每调用独立 embed+search。
     */
    public Mono<EmbeddingSearchResult<TextSegment>> recallMono(String query, int maxResults, double minScore) {
        if (query == null || query.isBlank()) {
            return Mono.just(new EmbeddingSearchResult<>(List.of()));
        }
        if (!properties.isEnabled()) {
            return Mono.fromCallable(() -> embedAndSearchBlocking(query, maxResults, minScore))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        RagVectorFlightKey key = RagVectorFlightKey.of(query, maxResults, minScore, properties.getEmbeddingModelId());
        return inflight.computeIfAbsent(key, k ->
                Mono.defer(() -> Mono.fromCallable(() -> loadWithHedge(k))
                                .subscribeOn(Schedulers.boundedElastic()))
                        .timeout(Duration.ofSeconds(properties.getMonoTimeoutSeconds()))
                        .cache()
                        .doFinally(sig -> inflight.remove(k))
        );
    }

    private EmbeddingSearchResult<TextSegment> loadWithHedge(RagVectorFlightKey key) {
        CompletableFuture<EmbeddingSearchResult<TextSegment>> main =
                CompletableFuture.supplyAsync(() -> vectorLoadThroughRedisAndLock(key), cacheExecutor);
        CompletableFuture<EmbeddingSearchResult<TextSegment>> outcome = new CompletableFuture<>();
        main.whenComplete((r, ex) -> {
            if (outcome.isDone()) {
                return;
            }
            if (ex == null) {
                outcome.complete(r);
            } else {
                outcome.completeExceptionally(ex);
            }
        });
        hedgeScheduler.schedule(() -> {
            if (outcome.isDone() || main.isDone()) {
                return;
            }
            CompletableFuture.supplyAsync(() -> vectorLoadThroughRedisAndLock(key), cacheExecutor)
                    .whenComplete((r, ex) -> {
                        if (outcome.isDone()) {
                            return;
                        }
                        if (ex == null) {
                            outcome.complete(r);
                        } else {
                            outcome.completeExceptionally(ex);
                        }
                    });
        }, properties.getHedgeDelayMs(), TimeUnit.MILLISECONDS);
        try {
            return outcome.get(properties.getMonoTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("RAG vector recall timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RAG vector recall interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(c);
        }
    }

    public EmbeddingSearchResult<TextSegment> embedAndSearchBlocking(String query, int maxResults, double minScore) {
        Response<Embedding> qResp = embeddingModel.embed(query);
        Embedding queryEmb = qResp.content();
        EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        return redisEmbeddingStore.search(req);
    }

    private EmbeddingSearchResult<TextSegment> vectorLoadThroughRedisAndLock(RagVectorFlightKey key) {
        String suffix = key.redisHashSuffix();
        String cacheKey = RagCoalesceRedisKeys.cacheKey(suffix);
        String lockKey = RagCoalesceRedisKeys.lockKey(suffix);
        String topicKeyStr = RagCoalesceRedisKeys.topicKey(suffix);
        RLock lock = redissonClient.getLock(lockKey);

        for (int attempt = 0; attempt < properties.getMaxLoadAttempts(); attempt++) {
            EmbeddingSearchResult<TextSegment> fromRedis = tryReadCache(cacheKey);
            if (fromRedis != null) {
                return fromRedis;
            }

            boolean locked = false;
            try {
                locked = lock.tryLock(0, properties.getLockLeaseSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("rag vector lock interrupted", e);
            }

            if (locked) {
                try {
                    EmbeddingSearchResult<TextSegment> doubleCheck = tryReadCache(cacheKey);
                    if (doubleCheck != null) {
                        return doubleCheck;
                    }
                    EmbeddingSearchResult<TextSegment> computed = embedAndSearchBlocking(
                            key.normalizedQuery(),
                            key.maxResults(),
                            key.minScore()
                    );
                    writeCacheIfFits(cacheKey, computed);
                    redissonClient.getTopic(topicKeyStr).publish(WAKE_MSG);
                    return computed;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            log.info("RAG vector: lock busy, await topic then retry, attempt {}/{}",
                    attempt + 1, properties.getMaxLoadAttempts());
            waitForPubSubWake(topicKeyStr);
            sleepWakeJitter();
        }

        throw new RuntimeException("RAG vector recall overloaded, retry later");
    }

    private void sleepWakeJitter() {
        int lo = Math.min(properties.getWakeJitterMinMs(), properties.getWakeJitterMaxMs());
        int hi = Math.max(properties.getWakeJitterMinMs(), properties.getWakeJitterMaxMs());
        long jitter = lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("rag vector wake jitter interrupted", e);
        }
    }

    /**
     * @return null 表示未命中或解析失败（按未命中处理）
     */
    private EmbeddingSearchResult<TextSegment> tryReadCache(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            String modelInCache = codec.peekModelId(json);
            if (modelInCache != null && !properties.getEmbeddingModelId().equals(modelInCache)) {
                log.debug("RAG vector cache model mismatch, skip key={}", cacheKey);
                return null;
            }
            return codec.fromJson(json);
        } catch (Exception e) {
            log.warn("RAG vector cache read failed, key={}", cacheKey, e);
            return null;
        }
    }

    private void writeCacheIfFits(String cacheKey, EmbeddingSearchResult<TextSegment> result) {
        try {
            RagVectorSearchCachePayload payload = codec.toPayload(
                    properties.getEmbeddingModelId(),
                    result,
                    properties.getMaxCachedMatches());
            String json = codec.toJson(payload);
            int bytes = codec.utf8ByteLength(json);
            if (bytes > properties.getMaxCachedPayloadBytes()) {
                log.debug("RAG vector cache payload too large ({} bytes), skip SET", bytes);
                return;
            }
            stringRedisTemplate.opsForValue().set(cacheKey, json, properties.getTtlSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("RAG vector cache serialize failed, key={}", cacheKey, e);
        }
    }

    private void waitForPubSubWake(String topicKeyStr) {
        RTopic topic = redissonClient.getTopic(topicKeyStr);
        CountDownLatch latch = new CountDownLatch(1);
        int listenerId = topic.addListener(String.class, (channel, msg) -> {
            if (WAKE_MSG.equals(msg)) {
                latch.countDown();
            }
        });
        try {
            int awaitMs = Math.max(100, properties.getPubSubAwaitMs());
            if (!latch.await(awaitMs, TimeUnit.MILLISECONDS)) {
                log.warn("RAG vector Pub/Sub wake timeout, topic={}", topicKeyStr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            topic.removeListener(listenerId);
        }
    }

    @PreDestroy
    public void shutdownHedgeScheduler() {
        hedgeScheduler.shutdown();
    }
}
