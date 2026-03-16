package com.aiconsultant.consultant.interceptor;

import cn.hutool.core.bean.BeanUtil;

import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;


import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.aiconsultant.consultant.utils.Constants.PREFIX_TOKEN;
import static com.aiconsultant.consultant.utils.Constants.TOKEN_TTL;


@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        Cookie[] cookies = request.getCookies();
        String token = null;

        // 2. 遍历查找名为 "token" 的 Cookie
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }



        if (token == null) {
            return true;
        }
        // 2.基于token获取redis的用户
        Map<Object,Object> user = stringRedisTemplate.opsForHash().entries(PREFIX_TOKEN+token);
        // 3.判断用户是否存在
        if (user.isEmpty()) {
            // 4.不存在
            return true;
        }
        // 5.将查询到的Hash数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user,new UserDTO(),false);
        // 6.存在，保存用户信息到Threadlocal
        UserHolder.saveUser(userDTO);
        // 7.刷新token有效期
        String key = PREFIX_TOKEN+token;
        stringRedisTemplate.expire(key,TOKEN_TTL, TimeUnit.MINUTES);
        // 8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
