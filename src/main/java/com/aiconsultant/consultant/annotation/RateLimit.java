package com.aiconsultant.consultant.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 限流单元的 Key，支持占位符或特定标识
     */
    String key() default "default_lock";

    /**
     * 每秒产生的令牌数 (速率)
     */
    double rate() default 1.0;

    /**
     * 令牌桶的最大容量
     */
    long capacity() default 10;
}
