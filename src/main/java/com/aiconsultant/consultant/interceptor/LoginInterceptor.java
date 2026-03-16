package com.aiconsultant.consultant.interceptor;

import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;



@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (UserHolder.getUser() == null) {
            // 获取请求的类型
            String accept = request.getHeader("Accept");

            // 逻辑：如果是请求页面 (HTML)，直接重定向
            if (accept != null && accept.contains("text/html")) {
                response.sendRedirect("/login.html");
            } else {
                // 逻辑：如果是 AJAX/Fetch 接口请求，返回 401
                response.setStatus(401);
            }
            return false; // 拦截执行
        }
        return true; // 放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
