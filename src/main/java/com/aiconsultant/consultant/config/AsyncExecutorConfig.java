package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.decorator.AIDecorator;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncExecutorConfig {


    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("AI-Worker-");

        // 【核心大活】：设置任务装饰器
        executor.setTaskDecorator(new AIDecorator());

        executor.initialize();
        return executor;
    }

}
