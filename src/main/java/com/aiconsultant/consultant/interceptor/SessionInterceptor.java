package com.aiconsultant.consultant.interceptor;

import cn.hutool.core.util.StrUtil;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionInterceptor implements HandlerInterceptor {

    @Resource
    private UserSessionService userSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String sessionIdStr = request.getHeader("X-Session-Id");

        // 1. 如果是 -1，代表新会话，放行
        // 注意：此时 SessionHolder 里面是空的，Service 层看到 null 就会去创建新 ID
        if ("-1".equals(sessionIdStr)) {
            return true;
        }

        if (StrUtil.isBlank(sessionIdStr)) {
            return false;
        }

        Long sessionId = Long.valueOf(sessionIdStr);
        Long userId = UserHolder.getUser().getId();

        // 2. 校验归属权
        boolean isValid = userSessionService.query()
                .eq("id",sessionId)
                .eq("user_id",userId)
                .exists();

        if (isValid) {
            // 【核心改动】：校验通过后，存入上下文
            SessionHolder.saveSessionId(sessionId);
            return true;
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 【核心操作】：请求结束，必须清除 ThreadLocal，否则线程复用会导致下个请求拿到旧数据
        SessionHolder.removeSession();

    }
}
