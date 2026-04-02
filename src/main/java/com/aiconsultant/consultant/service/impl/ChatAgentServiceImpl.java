package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.ChatAgent;
import com.aiconsultant.consultant.mapper.ChatAgentMapper;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.ChatAgentDescription;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.ChatAgentService;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.aiconsultant.consultant.utils.Constants.AGENT_CACHE_KEY;
import static com.aiconsultant.consultant.utils.Constants.AGENT_LOCK_KEY;
import static com.aiconsultant.consultant.utils.Constants.AGENT_TOPIC_KEY;

@Slf4j
@Service
public class ChatAgentServiceImpl extends ServiceImpl<ChatAgentMapper, ChatAgent> implements ChatAgentService {

    private static final int MAX_LOAD_ATTEMPTS = 3;
    /** 主路径超过该时间仍未结束则最多再派 1 条对冲线程 */
    private static final long HEDGE_DELAY_MS = 1500;
    /** 与 {@link #getAgentById} 外层 3s 超时对齐：单次加载整体等待上限 */
    private static final long LOAD_WAIT_SECONDS = 3;
    /** 连续失败次数达到该值触发熔断 */
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    /** 熔断打开时，约 10% 请求仍走加载以探测恢复 */
    private static final int CIRCUIT_SENTINEL_THRESHOLD = 10;
    /**
     * 熔断持续时间阶梯（毫秒）：首次 trip 用 [0]=30s；在打开状态下每次快速失败（非哨兵）后按阶梯延长。
     * 索引 1..5 对应 1m、2m、4m、8m、10m（上限）
     */
    private static final long[] CIRCUIT_OPEN_DURATION_MS = {
            30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 600_000L
    };

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Executor cacheExecutor = Schedulers.boundedElastic()::schedule;
    private final ScheduledExecutorService hedgeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-agent-hedge");
        t.setDaemon(true);
        return t;
    });

    /** 熔断关闭时间戳；{@code System.currentTimeMillis()} 小于该值视为熔断中 */
    private volatile long circuitOpenUntilMillis;
    /** 连续技术失败次数（智能体不存在等业务结果不计入） */
    private final AtomicInteger consecutiveLoadFailures = new AtomicInteger(0);
    /**
     * 熔断期内每次「非哨兵的快速失败」后递增，用于选择 {@link #CIRCUIT_OPEN_DURATION_MS} 延长窗口
     */
    private final AtomicInteger circuitRejectEscalation = new AtomicInteger(0);

    /**
     * L1：Caffeine。未命中时调用 {@link #loadAgentOptional}，其中再走 Redis → 锁 → DB → Pub/Sub 重试链路。
     */
    private final AsyncLoadingCache<Long, Optional<ChatAgentDTO>> agentCache = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .buildAsync(this::loadAgentOptional);

    private final AsyncLoadingCache<String, List<ChatAgentDescription>> agentListCache = Caffeine.newBuilder()
            .maximumSize(4)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .buildAsync(this::loadAgentDescriptionList);

    @Override
    public Mono<ChatAgentDTO> getAgentById(Long id) {
        return Mono.fromFuture(agentCache.get(id))
                .timeout(Duration.ofSeconds(3))
                .flatMap(optional -> optional
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new RuntimeException("智能体不存在"))))
                .onErrorMap(TimeoutException.class, e -> new RuntimeException("系统繁忙，请稍后再试", e));
    }

    /**
     * 仅当 Caffeine 需要加载该 key 时调用（未命中或异步刷新）。
     * 顺序：熔断检查 → 主路径（Redis → 抢锁 → DB…）；主路径超过 {@link #HEDGE_DELAY_MS}ms 仍未结束则最多再派 1 条对冲。
     */
    private CompletableFuture<Optional<ChatAgentDTO>> loadAgentOptional(Long id, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            checkCircuitBeforeLoad();
            CompletableFuture<Optional<ChatAgentDTO>> main = CompletableFuture.supplyAsync(
                    () -> loadThroughRedisAndLock(id), cacheExecutor);
            CompletableFuture<Optional<ChatAgentDTO>> outcome = new CompletableFuture<>();
            main.whenComplete((r, ex) -> {
                if (outcome.isDone()) {
                    return;
                }
                if (ex == null) {
                    outcome.complete(r);
                    recordLoadSuccess();
                } else {
                    outcome.completeExceptionally(ex);
                    recordLoadFailure();
                }
            });
            hedgeScheduler.schedule(() -> {
                if (outcome.isDone() || main.isDone()) {
                    return;
                }
                CompletableFuture.supplyAsync(() -> loadThroughRedisAndLock(id), cacheExecutor)
                        .whenComplete((r, ex) -> {
                            if (outcome.isDone()) {
                                return;
                            }
                            if (ex == null) {
                                outcome.complete(r);
                                recordLoadSuccess();
                            } else {
                                outcome.completeExceptionally(ex);
                                recordLoadFailure();
                            }
                        });
            }, HEDGE_DELAY_MS, TimeUnit.MILLISECONDS);
            try {
                return outcome.get(LOAD_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                recordLoadFailure();
                throw new RuntimeException("系统繁忙，请稍后再试", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordLoadFailure();
                throw new RuntimeException("智能体加载被中断", e);
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(c);
            }
        }, cacheExecutor);
    }

    private void checkCircuitBeforeLoad() {
        long now = System.currentTimeMillis();
        if (now >= circuitOpenUntilMillis) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) < CIRCUIT_SENTINEL_THRESHOLD) {
            log.debug("智能体加载熔断哨兵放行探测");
            return;
        }
        extendCircuitOnFastReject();
        throw new RuntimeException("系统服务降级中，请稍后再试");
    }

    /** 熔断期内非哨兵请求：快速失败并按阶梯延长熔断窗口 */
    private void extendCircuitOnFastReject() {
        int level = circuitRejectEscalation.incrementAndGet();
        int idx = Math.min(level, CIRCUIT_OPEN_DURATION_MS.length - 1);
        long dur = CIRCUIT_OPEN_DURATION_MS[idx];
        circuitOpenUntilMillis = System.currentTimeMillis() + dur;
        log.warn("熔断期内快速失败，延长熔断窗口至 {} ms (escalationLevel={})", dur, level);
    }

    private void recordLoadSuccess() {
        consecutiveLoadFailures.set(0);
        circuitRejectEscalation.set(0);
        circuitOpenUntilMillis = 0L;
    }

    private void recordLoadFailure() {
        int n = consecutiveLoadFailures.incrementAndGet();
        if (n >= CIRCUIT_FAILURE_THRESHOLD) {
            tripCircuitBreaker();
        }
    }

    /** 连续 {@link #CIRCUIT_FAILURE_THRESHOLD} 次技术失败后打开熔断，首次持续 30s */
    private void tripCircuitBreaker() {
        consecutiveLoadFailures.set(0);
        circuitRejectEscalation.set(0);
        circuitOpenUntilMillis = System.currentTimeMillis() + CIRCUIT_OPEN_DURATION_MS[0];
        log.warn("智能体加载连续失败达到阈值，熔断已打开 {} ms", CIRCUIT_OPEN_DURATION_MS[0]);
    }

    @PreDestroy
    public void shutdownHedgeScheduler() {
        hedgeScheduler.shutdown();
    }

    private Optional<ChatAgentDTO> loadThroughRedisAndLock(Long id) {
        String cacheKey = AGENT_CACHE_KEY + id;
        String lockKey = AGENT_LOCK_KEY + id;
        RLock lock = redissonClient.getLock(lockKey);

        for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
            Optional<ChatAgentDTO> fromRedis = readFromRedis(cacheKey);
            if (fromRedis != null) {
                return fromRedis;
            }

            boolean locked = false;
            try {
                locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("获取智能体被中断", e);
            }

            if (locked) {
                try {
                    Optional<ChatAgentDTO> doubleCheck = readFromRedis(cacheKey);
                    if (doubleCheck != null) {
                        return doubleCheck;
                    }
                    return loadFromDbAndFillRedis(id, cacheKey);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            log.info("未抢到分布式锁，等待 Pub/Sub 唤醒后重试, id: {}, attempt: {}/{}", id, attempt + 1, MAX_LOAD_ATTEMPTS);
            waitForPubSubWake(id);
            long jitter = ThreadLocalRandom.current().nextLong(50, 200);
            try {
                Thread.sleep(jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待智能体加载被中断", e);
            }
        }

        throw new RuntimeException("当前咨询人数过多，请稍后再试");
    }

    /**
     * @return null 表示 Redis 未命中或异常时按未命中处理；Optional.empty() 表示负缓存（不存在）；Optional.of 为命中
     */
    private Optional<ChatAgentDTO> readFromRedis(String cacheKey) {
        try {
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            if ("null".equals(json)) {
                return Optional.empty();
            }
            return Optional.of(JSONUtil.toBean(json, ChatAgentDTO.class));
        } catch (Exception e) {
            log.warn("读取 Redis 智能体缓存异常, key: {}", cacheKey, e);
            return null;
        }
    }

    private Optional<ChatAgentDTO> loadFromDbAndFillRedis(Long id, String cacheKey) {
        ChatAgent agent = this.getById(id);
        if (agent == null) {
            stringRedisTemplate.opsForValue().set(cacheKey, "null", 5, TimeUnit.MINUTES);
            redissonClient.getTopic(AGENT_TOPIC_KEY + id).publish("WAKE_UP");
            return Optional.empty();
        }
        ChatAgentDTO dto = new ChatAgentDTO();
        BeanUtil.copyProperties(agent, dto);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(dto), 1, TimeUnit.DAYS);
        redissonClient.getTopic(AGENT_TOPIC_KEY + id).publish("WAKE_UP");
        return Optional.of(dto);
    }

    private void waitForPubSubWake(Long id) {
        String topicKey = AGENT_TOPIC_KEY + id;
        RTopic topic = redissonClient.getTopic(topicKey);
        CountDownLatch latch = new CountDownLatch(1);
        int listenerId = topic.addListener(String.class, (channel, msg) -> {
            if ("WAKE_UP".equals(msg)) {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                log.warn("等待智能体 Pub/Sub 唤醒超时, id: {}", id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            topic.removeListener(listenerId);
        }
    }

    private CompletableFuture<List<ChatAgentDescription>> loadAgentDescriptionList(String key, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Caffeine 加载智能体列表 key: {}", key);
            List<ChatAgent> agents = this.list();
            if (agents == null || agents.isEmpty()) {
                return new ArrayList<>();
            }
            return agents.stream().map(agent -> {
                ChatAgentDescription dto = new ChatAgentDescription();
                dto.setId(agent.getId());
                dto.setName(agent.getName());
                dto.setDescription(agent.getDescription());
                return dto;
            }).collect(Collectors.toList());
        }, cacheExecutor);
    }

    @Override
    public Mono<Result> getAgentDescriptionList() {
        return Mono.fromFuture(agentListCache.get("ALL"))
                .timeout(Duration.ofSeconds(15))
                .onErrorMap(e -> new RuntimeException("系统繁忙，请稍后再试", e))
                .map(Result::ok);
    }
}
