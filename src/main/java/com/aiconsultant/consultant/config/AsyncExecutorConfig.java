package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        executor.setTaskDecorator(runnable -> {
            // 1. 此时还在 Tomcat 线程，先把上下文“取出来”
            UserDTO user = UserHolder.getUser();
            Long sessionId = SessionHolder.getSessionId();

            return () -> {
                try {
                    // 2. 此时已经到了 异步线程，把上下文“塞进去”
                    UserHolder.saveUser(user);
                    SessionHolder.saveSessionId(sessionId);
                    // 3. 执行真正的业务逻辑
                    runnable.run();
                } finally {
                    // 4. 执行完一定要清理，防止线程复用污染
                    UserHolder.removeUser();
                    SessionHolder.removeSession();
                }
            };
        });

        executor.initialize();
        return executor;
    }

}
