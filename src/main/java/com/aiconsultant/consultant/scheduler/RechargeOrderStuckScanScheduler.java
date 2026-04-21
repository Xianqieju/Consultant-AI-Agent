package com.aiconsultant.consultant.scheduler;

import com.aiconsultant.consultant.entity.RechargeOrder;
import com.aiconsultant.consultant.service.RechargeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 补偿：MQ TTL/死信丢失、消费失败时，仍可能长期停留在「待支付」的订单。
 * <p>
 * 多实例：每台 JVM 的 {@link Scheduled} 仍会按各自时钟触发，但同一时刻只有一个节点能拿到
 * {@link RLock} 并执行扫描；其它节点快速返回，不会打满数据库。
 * <p>
 * 「全集群上一次成功扫描时间」写入 Redis {@link #LAST_SUCCESS_AT_KEY}，供监控/排查；它不会改写各节点本地
 * {@code @Scheduled} 的下次触发时刻（Java 无法在进程间同步 Timer），若需全局节流可读取该 key 在锁内提前 return。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.recharge.stuck-scan.enabled", havingValue = "true", matchIfMissing = true)
public class RechargeOrderStuckScanScheduler {

    static final String LOCK_KEY = "lock:job:recharge:stuck-scan";
    static final String LAST_SUCCESS_AT_KEY = "job:recharge:stuck-scan:last-success-at";

    private final RedissonClient redissonClient;
    private final RechargeOrderService rechargeOrderService;

    @Value("${app.recharge.stuck-scan.min-age-minutes:18}")
    private int minAgeMinutes;
    @Value("${app.recharge.stuck-scan.batch-size:40}")
    private int batchSize;
    @Value("${app.recharge.stuck-scan.lock-wait-seconds:0}")
    private long lockWaitSeconds;
    @Value("${app.recharge.stuck-scan.lock-lease-minutes:10}")
    private long lockLeaseMinutes;
    @Value("${app.recharge.stuck-scan.min-interval-since-last-run-ms:0}")
    private long minIntervalSinceLastRunMs;

    /**
     * fixedDelay：上一轮结束后再隔 fixedDelayMs 触发，避免任务重叠堆积。
     */
    @Scheduled(fixedDelayString = "${app.recharge.stuck-scan.fixed-delay-ms:300000}")
    public void scanStuckPendingOrders() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            long leaseSeconds = TimeUnit.MINUTES.toSeconds(lockLeaseMinutes);
            locked = lock.tryLock(lockWaitSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("充值订单补偿扫描获取锁被中断");
            return;
        }
        if (!locked) {
            log.debug("充值订单补偿扫描跳过：未获取到分布式锁（其它节点正在执行或锁被占用）");
            return;
        }
        try {
            if (shouldSkipByGlobalInterval()) {
                return;
            }
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(minAgeMinutes);
            List<RechargeOrder> batch = rechargeOrderService.lambdaQuery()
                    .eq(RechargeOrder::getStatus, 0)
                    .isNotNull(RechargeOrder::getCreateTime)
                    .lt(RechargeOrder::getCreateTime, threshold)
                    .orderByAsc(RechargeOrder::getCreateTime)
                    .last("LIMIT " + batchSize)
                    .list();
            if (batch.isEmpty()) {
                touchLastSuccessAt();
                return;
            }
            log.info("充值订单补偿扫描：待处理 {} 条（createTime < {}）", batch.size(), threshold);
            for (RechargeOrder row : batch) {
                try {
                    rechargeOrderService.reconcileTimeoutOrder(row.getOrderNo());
                } catch (Exception e) {
                    log.error("充值订单补偿单条失败 orderNo={}", row.getOrderNo(), e);
                }
            }
            touchLastSuccessAt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean shouldSkipByGlobalInterval() {
        if (minIntervalSinceLastRunMs <= 0) {
            return false;
        }
        RBucket<Long> bucket = redissonClient.getBucket(LAST_SUCCESS_AT_KEY, LongCodec.INSTANCE);
        Long prev = bucket.get();
        if (prev == null) {
            return false;
        }
        long delta = System.currentTimeMillis() - prev;
        if (delta < minIntervalSinceLastRunMs) {
            log.debug("充值订单补偿扫描跳过：距上次全集群成功不足 {} ms", minIntervalSinceLastRunMs);
            return true;
        }
        return false;
    }

    private void touchLastSuccessAt() {
        redissonClient.getBucket(LAST_SUCCESS_AT_KEY, LongCodec.INSTANCE)
                .set(System.currentTimeMillis(), 7, TimeUnit.DAYS);
    }
}
