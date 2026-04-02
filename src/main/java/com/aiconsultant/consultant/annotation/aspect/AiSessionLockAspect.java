package com.aiconsultant.consultant.annotation.aspect;

import com.aiconsultant.consultant.annotation.AiSessionLock;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.aiconsultant.consultant.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;


@Aspect
@Component
@Slf4j
public class AiSessionLockAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker; // 注入你的 ID 生成器

    @Around("@annotation(aiSessionLock)")
    public Object around(ProceedingJoinPoint joinPoint, AiSessionLock aiSessionLock) throws Throwable {
        // 1. 获取当前环境信息
        Long userId = UserHolder.getUser().getId();
        Long sessionId = SessionHolder.getSessionId();

        // 2. 预处理：如果是新会话 (-1 或 null)，在 AOP 层直接生成并占位
        if (sessionId == null || sessionId <= 0) {
            sessionId = snowflakeIdWorker.nextId();
            // 立即回填到 Holder，确保进入方法后业务逻辑拿到的是同一个 ID
            SessionHolder.saveSessionId(sessionId);
            log.info("AOP 预生成新会话 ID: {}", sessionId);
        }

        // 3. 锁定 sessionId（此时 sessionId 必然有值且唯一）
        final String lockKey = "lock:ai:session:" + sessionId;
        final Long finalSessionId = sessionId; // 闭包引用

        // 4. 尝试抢占 Redis 锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "RUNNING",
                Duration.ofSeconds(aiSessionLock.expire()));

        if (Boolean.FALSE.equals(success)) {
            log.warn("会话 {} 正在运行中，拦截重复请求。", finalSessionId);
            throw new RuntimeException("AI 正在全力思考中，请稍后再试...");
        }

        log.info("会话 {} 锁定成功，准备执行业务逻辑。", finalSessionId);

        try {
            // 执行业务方法（即 streamChatProcess）
            Object result = joinPoint.proceed();

            if (result instanceof Flux) {
                // 5. 针对响应式流，使用 doFinally 确保锁释放
                // 这里 lockKey 是 final 的，doFinally 会正确捕获它
                return ((Flux<?>) result).doFinally(signalType -> {
                    log.info("会话 {} 任务终结 (信号: {}), 释放锁。", finalSessionId, signalType);
                    redisTemplate.delete(lockKey);
                });
            }

            // 同步方法执行完直接释放
            redisTemplate.delete(lockKey);
            return result;

        } catch (Throwable e) {
            // 业务执行前报错，立即释放锁
            redisTemplate.delete(lockKey);
            throw e;
        }
    }
}
