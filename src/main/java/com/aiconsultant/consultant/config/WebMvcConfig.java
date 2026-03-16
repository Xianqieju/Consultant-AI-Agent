package com.aiconsultant.consultant.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 注入我们之前配置好的、带有 TaskDecorator 的线程池
    @Resource(name = "aiExecutor")
    private ThreadPoolTaskExecutor aiExecutor;

    // ... 你的拦截器配置保持不变 ...

    /**
     * 【核心改动】：接管 Spring MVC 的异步处理逻辑
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 让 Spring MVC 也在这个线程池里进行异步上下文调度
        configurer.setTaskExecutor(aiExecutor);
        // 可选：设置异步请求超时时间（例如 60 秒）
        configurer.setDefaultTimeout(60000L);
    }
}
