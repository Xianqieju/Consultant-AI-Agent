package com.aiconsultant.consultant.service.impl;

import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.jwt.JWTUtil;
import com.aiconsultant.consultant.exception.BusinessException;
import com.aiconsultant.consultant.manager.LocalTokenBlacklistManager;
import com.aiconsultant.consultant.utils.JwtUtil;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.User;
import com.aiconsultant.consultant.entity.UserWallet;
import com.aiconsultant.consultant.mapper.UserMapper;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.aiconsultant.consultant.utils.Constants.*;
import static com.aiconsultant.consultant.utils.PasswordUtils.encode;
import static com.aiconsultant.consultant.utils.RedisScript.LOCK_SCRIPT;
import static com.aiconsultant.consultant.utils.RedisScript.UNLOCK_SCRIPT;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserWalletMapper userWalletMapper;
    @Autowired
    private RBloomFilter<Long> userBloomFilter;
    @Autowired
    SnowflakeIdWorker snowflakeIdWorker;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private LocalTokenBlacklistManager localTokenBlacklistManager;
    @Autowired
    private RabbitTemplate rabbitTemplate;



    @Override
    public Result login(String username, String password) {
        // 1. 基础校验
        if (username == null || password == null) {
            return Result.fail("参数不能为空！");
        }

        User user = query().eq("username", username).one();

        // 细节优化：统一返回“用户名或密码错误”，防范用户名枚举攻击
        if (user == null || !encode(password).equals(user.getPassword())) {
            return Result.fail("用户名或密码错误！");
        }

        // 2. 【核心改造】：生成 Long 类型的会话 ID (用于适配咆哮位图)
        // 放弃 UUID，改用雪花算法生成数字 ID，这是 RoaringBitmap 能高效压缩的物理前提
        Long tokenId = snowflakeIdWorker.nextId();
        Long userId = user.getId();

        // 3. 组装 JWT 载荷 (Payload)
        // 遵循无状态原则：只放最核心的身份标识，绝不放敏感信息(如密码)
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tokenId", tokenId);
        claims.put("nickname", user.getNickname()); // 视前端需求，可适当放入不敏感数据减少查库

        // 4. 签发双 Token (需配合自定义的 JwtUtil 工具类)
        // Access Token (AT)：高频使用，短有效时长（例如 2 小时）

        String accessToken = JwtUtil.createToken(claims, 2 * 60 * 60 * 1000L);
        // Refresh Token (RT)：低频使用，长有效时长（例如 7 天）
        String refreshToken = JwtUtil.createToken(claims, 7 * 24 * 60 * 60 * 1000L);

        // 5. 组装返回结果
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);

        // 【注】：彻底删除了 stringRedisTemplate.opsForHash().putAll(...)
        // 登录接口现在是 O(1) 的内存开销，Redis 零负担。

        return Result.ok(tokenMap);
    }

    @Override
    @Transactional
    public Result register(User user) {
        String lockValue = UUID.randomUUID().toString();

        Long result = stringRedisTemplate.execute(
                LOCK_SCRIPT,
                Arrays.asList(PREFIX_REGISTER_PHONE+user.getPhone(), PREFIX_REGISTER_USERNAME+user.getUsername()),
                lockValue, REGISTER_TTL
        );

        if (result == null || result == 0) {
            return Result.fail("系统繁忙或账号已被占用，请稍后再试");
        }

        try{
            long count = query().eq("phone",user.getPhone())
                    .or()
                    .eq("user_name",user.getUsername())
                    .count();
            if(count>0){
                return Result.fail("手机号或者账号被重复注册，请重试！");
            }
            user.setPassword(encode(user.getPassword()));
            save(user);
            UserWallet wallet = new UserWallet();
            // 直接获取 user 刚刚生成的 ID 赋值给钱包
            wallet.setId(user.getId());
            wallet.setQuota(DEFAULT_QUOTA); // 设置初始额度，建议从常量中读取
            wallet.setVersion(1);
            wallet.setUpdateTime(LocalDateTime.now());
            userWalletMapper.insert(wallet);
            userBloomFilter.add(user.getId());
            return Result.ok();
        }finally{
            stringRedisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Arrays.asList(PREFIX_REGISTER_PHONE+user.getPhone(), PREFIX_REGISTER_USERNAME+user.getUsername()),
                    lockValue
            );
        }
    }

    @Override
    public Result refreshToken(String refreshToken) {
        // 1. 基础校验
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            // 如果我们后续上了全局异常处理器，这里可以直接 throw new BusinessException(40101, "凭证缺失");
            return Result.fail(40101+"");
        }

        try {
            // 2. 解析并验签 (JWTUtil 内部会自动校验是否过期)
            Claims claims = JwtUtil.parseToken(refreshToken);

            // 3. 提取关键信息
            Long userId = Long.valueOf(claims.get("userId").toString());
            Long tokenId = Long.valueOf(claims.get("tokenId").toString());
            String nickname = (String) claims.get("nickname");

            // 4. 生成新的一对 Token (滑动窗口续期)
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("userId", userId);
            newClaims.put("tokenId", tokenId); // 保持 tokenId 不变，用于追踪会话
            newClaims.put("nickname", nickname);

            // 签发新的短效 AT (2小时) 和 长效 RT (7天)
            String newAT = JwtUtil.createToken(newClaims, 2 * 60 * 60 * 1000L);
            String newRT = JwtUtil.createToken(newClaims, 7 * 24 * 60 * 60 * 1000L);

            // 5. 组装返回结果
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", newAT);
            tokens.put("refreshToken", newRT);

            return Result.ok(tokens);

        } catch (BusinessException e) {
            // 捕获到我们自定义的 40103 异常（RT 也过期了）
            return Result.fail(e.getCode()+"");
        } catch (Exception e) {
            // 签名错误或被篡改
            return Result.fail(40102+"");
        }
    }

    // 在 UserServiceImpl 中新增 logout 方法
    //@Override
    public Result logout(HttpServletRequest request) {
        // 这里假设拦截器已经放行，直接提取 token
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

        if (token != null && !token.trim().isEmpty()) {
            try {
                Claims claims = JwtUtil.parseToken(token);
                Long tokenId = Long.valueOf(claims.get("tokenId").toString());

                // 1. 写入本机黑名单
                localTokenBlacklistManager.addBlacklist(tokenId);

                // 2. 发送 MQ 广播给其他节点 (注意 routingKey 在 Fanout 模式下填空字符串即可)
                rabbitTemplate.convertAndSend("token.blacklist.fanout", "", tokenId);

                log.info("用户主动登出，TokenId: {} 已拉黑并广播", tokenId);
            } catch (Exception e) {
                // 解析失败说明 Token 已失效或被篡改，无需额外处理
                log.warn("注销时解析 Token 失败，直接忽略");
            }
        }

        return Result.ok("注销成功");
    }
}
