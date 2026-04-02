package com.aiconsultant.consultant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiSessionLock {
    // 锁的过期时间，防止程序异常导致死锁（建议设置比 AI 最长吟唱时间略长）
    long expire() default 60;
}
