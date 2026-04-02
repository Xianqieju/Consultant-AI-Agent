package com.aiconsultant.consultant.manager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ReactivePermitManager {

    private final int maxConcurrency = 100;
    private final AtomicInteger activeCount = new AtomicInteger(0);

    // 使用 Deque 方便我们从队列中定位并删除特定的“老登”
    private final ConcurrentLinkedDeque<Sinks.One<Void>> waitQueue = new ConcurrentLinkedDeque<>();

    private final ConcurrentHashMap<Sinks.One<Void>, Long> permitRegistry = new ConcurrentHashMap<>();

    public Mono<Void> acquire() {
        return Mono.defer(() -> {
            while (true) {
                int current = activeCount.get();
                if (current < maxConcurrency) {
                    if (activeCount.compareAndSet(current, current + 1)) {
                        return Mono.empty();
                    }
                } else {
                    Sinks.One<Void> sink = Sinks.one();
                    // 注册到花名册并进入队列
                    permitRegistry.put(sink, System.currentTimeMillis());
                    waitQueue.offer(sink);

                    // 【核心增量】：安装“后悔药”监听器
                    // 当这个 Mono 因为超时或前端断连被取消时，主动从队列移除自己
                    return sink.asMono()
                            // 【O(1) 核心】：超时或取消时，直接从 Map 里抹掉，不用去遍历 Queue
                            .doOnCancel(() -> {
                                if (permitRegistry.remove(sink) != null) {
                                    log.info("【O(1) 剔除】老登任务已注销，排队人数: {}", permitRegistry.size());
                                }
                            });
                }
            }
        });
    }

    public void release() {
        while (true) {
            Sinks.One<Void> next = waitQueue.poll();
            if (next == null) {
                activeCount.decrementAndGet();
                releaseIfSlotAvailable();
                break;
            }

            // 【延迟删除检查】：如果花名册里没这个人了，说明它是已经注销的“老登”
            if (permitRegistry.remove(next) == null) {
                // 这是一个死掉的任务，直接跳过，找下一个
                continue;
            }

            // 这是一个活着的任务，发放许可证
            if (next.tryEmitEmpty().isSuccess()) {
                break;
            }
        }
    }

    private void releaseIfSlotAvailable() {
        if (activeCount.get() < maxConcurrency) {
            while (true) {
                Sinks.One<Void> next = waitQueue.poll();
                if (next == null) return;

                // 同样进行花名册检查
                if (permitRegistry.remove(next) == null) continue;

                if (activeCount.incrementAndGet() <= maxConcurrency) {
                    if (next.tryEmitEmpty().isSuccess()) {
                        break;
                    } else {
                        activeCount.decrementAndGet();
                    }
                } else {
                    activeCount.decrementAndGet();
                    // 没抢过，重新注册回花名册并塞回队首（注意这里需要一个 Deque 才能 offerFirst）
                    // 简化起见，这里直接重回队尾
                    permitRegistry.put(next, System.currentTimeMillis());
                    waitQueue.offer(next);
                    break;
                }
            }
        }
    }
}
