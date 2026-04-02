package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.interceptor.BloomFilterInterceptor;
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

    @Autowired
    private BloomFilterInterceptor bloomFilterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {//拦截器注册
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/login.html",
                        "/register.html",
                        "/user/login",
                        "/user/register",
                        "/user/refresh"
                )
                .order(1);

        // 换票仅依赖 Cookie 中的 refreshToken，不能要求已携带有效的 access token（token）
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/refresh")
                .order(0);

        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/chat/process",
                        "/chat/memory") // 只拦截对话相关接口
                .order(2); // 必须在 LoginInterceptor 之后

        registry.addInterceptor(bloomFilterInterceptor)
                .excludePathPatterns(
                        "/login.html",
                        "/register.html",
                        "/user/login",
                        "/user/register",
                        "/user/refresh"
                )
                .order(3);
    }
}
