package com.aiconsultant.consultant.interceptor;

import cn.hutool.core.bean.BeanUtil;

import com.aiconsultant.consultant.exception.BusinessException;
import com.aiconsultant.consultant.manager.LocalTokenBlacklistManager;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.utils.JwtUtil;
import com.aiconsultant.consultant.utils.UserHolder;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private LocalTokenBlacklistManager localTokenBlacklistManager;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头或 Cookie 中的 token
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        // 如果没有 Token，直接返回 401 未授权
        if (token == null || token.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 2. 解析 JWT 并验证有效期
        // 注意：JwtUtil.parseToken 内部已经包含了过期时间校验。
        // 如果 Token 已经被篡改，或者已经过了有效时间，parseToken 会抛出异常或返回 null
        Claims claims = JwtUtil.parseToken(token);

        if (claims == null) {
            // 验证失败或已过期，冷酷地返回 401
            // 前端收到 401 后，会主动拿着 Refresh Token 去调用单独的刷新接口
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Long tokenId = Long.valueOf(claims.get("tokenId").toString());

// 去查本地内存的黑名单
        if (localTokenBlacklistManager.isBlacklisted(tokenId)) {
            throw new BusinessException(40101, "当前会话已失效");
        }
        // 3. 校验通过，提取载荷信息并存入 ThreadLocal
        UserDTO userDTO = new UserDTO();
        // 从 Claims 中安全提取之前存入的 userId
        userDTO.setId(Long.valueOf(claims.get("userId").toString()));
        if (claims.get("nickname") != null) {
            userDTO.setNickname(claims.get("nickname").toString());
        }

        // 4. 保存用户信息到 ThreadLocal，供后续 Controller 使用
        UserHolder.saveUser(userDTO);

        // 5. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求处理完毕，务必清理 ThreadLocal，防止内存泄漏和上下文污染
        UserHolder.removeUser();
    }
}
