package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.interceptor.LoginInterceptor;
import com.aiconsultant.consultant.interceptor.RefreshTokenInterceptor;
import com.aiconsultant.consultant.interceptor.SessionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

    @Autowired
    private SessionInterceptor sessionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {//拦截器注册
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/login.html",
                        "/register.html",
                        "/user/login",
                        "/user/register"
                )
                .order(1);

        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/chat/process",
                        "/chat/memory") // 只拦截对话相关接口
                .order(1); // 必须在 LoginInterceptor 之后
    }
}
