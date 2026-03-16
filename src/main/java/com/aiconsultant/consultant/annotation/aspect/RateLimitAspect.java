package com.aiconsultant.consultant.annotation.aspect;

import com.aiconsultant.consultant.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key();
        // 获取分布式令牌桶实例
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("rate_limit:" + key);

        // 初始化限流配置：设置速率和容量
        // OVERALL 代表全局限流，1秒产生 rateLimit.rate() 个令牌
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.capacity(), 1, RateIntervalUnit.SECONDS);

        // 尝试获取 1 个令牌
        if (rateLimiter.tryAcquire(1)) {
            // 获取成功，执行业务逻辑
            return joinPoint.proceed();
        } else {
            // 获取失败，直接拒绝请求
            log.warn("接口触发限流限制，Key: {}", key);
            throw new RuntimeException("服务器繁忙，请稍后再试");
        }
    }
}
