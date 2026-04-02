package com.aiconsultant.consultant.interceptor;

import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class BloomFilterInterceptor implements HandlerInterceptor {

    @Autowired
    private RBloomFilter<Long> userBloomFilter;

    @Autowired
    private RBloomFilter<String> sessionBloomFilter;

    @Resource
    private UserSessionService userSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = UserHolder.getUser().getId();
        Long sessionId = SessionHolder.getSessionId(); // 假设前置拦截器已填入 Holder

        // 1. 基础校验：用户必须存在
        if (!userBloomFilter.contains(userId)) {
            log.warn("非法用户 ID: {}", userId);
            throw new RuntimeException("非法请求：用户状态异常");
        }

        // 2. 核心校验：会话归属权
        // 只有非新会话 (-1) 才需要进入布隆过滤器
        if (sessionId != null && sessionId > 0) {
            String compositeKey = userId + ":" + sessionId;

            // A. 第一阶段：布隆过滤器快速放行/拦截
            if (!sessionBloomFilter.contains(compositeKey)) {
                log.warn("布隆拦截：UserId {} 与 SessionId {} 映射不存在", userId, sessionId);
                throw new RuntimeException("非法会话访问");
            }

            // B. 第二阶段：由于布隆过滤器存在假阳性，进行最终的数据库精准校验
            // 此时进入这里的请求已经是被过滤后的“干净”请求，对数据库压力很小
            boolean isValid = userSessionService.query()
                    .eq("id", sessionId)
                    .eq("user_id", userId)
                    .exists();

            if (!isValid) {
                log.error("安全预警：布隆过滤器发生误判，数据库中不存在归属关系！Key: {}", compositeKey);
                throw new RuntimeException("会话校验未通过");
            }
        }

        return true;
    }
}
