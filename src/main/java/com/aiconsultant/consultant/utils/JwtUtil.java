package com.aiconsultant.consultant.utils;

import com.aiconsultant.consultant.exception.BusinessException;
import io.jsonwebtoken.*;

import java.util.Date;
import java.util.Map;

public class JwtUtil {

    // 密钥：这是系统的最高机密，绝不能泄露！面试时可以提到这应该放在环境变量或配置中心
    private static final String SECRET_KEY = "YourAgentSystemSecretKey!@#2026";

    /**
     * 签发 Token
     * @param claims 载荷（也就是你登录时塞进去的 userId, tokenId 等数据）
     * @param ttlMillis 有效时长（毫秒）
     */
    public static String createToken(Map<String, Object> claims, Long ttlMillis) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        return Jwts.builder()
                .setClaims(claims)           // 设置自定义数据
                .setIssuedAt(now)             // 签发时间
                .setExpiration(new Date(nowMillis + ttlMillis)) // 过期时间
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY) // 签名算法和密钥
                .compact();
    }

    /**
     * 解析 Token
     * @param token 客户端传回来的加密字符串
     * @return 包含所有数据的 Claims 对象
     */


    /**
     * 快捷获取数据：比如直接拿 userId
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? Long.valueOf(claims.get("userId").toString()) : null;
    }

    /**
     * 快捷获取数据：拿到后续咆哮位图最需要的 tokenId
     */
    public static Long getTokenId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? Long.valueOf(claims.get("tokenId").toString()) : null;
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 特别注意：这里抛出自定义异常或标记，告诉拦截器是“过期”
            throw new BusinessException(40103, "Token已过期");
        } catch (SignatureException | MalformedJwtException e) {
            throw new BusinessException(40102, "无效令牌");
        } catch (Exception e) {
            throw new BusinessException(40101, "认证失败");
        }
    }
}
