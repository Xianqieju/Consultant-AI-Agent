package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.User;
import com.aiconsultant.consultant.entity.UserWallet;
import com.aiconsultant.consultant.mapper.UserMapper;
import com.aiconsultant.consultant.mapper.UserWalletMapper;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.UserService;
import jakarta.annotation.Resource;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserWalletMapper userWalletMapper;

    @Override
    public Result login(String username, String password) {
        if(username==null || password==null){
            return Result.fail("登录失败！");
        }
        User user = query().eq("username",username).one();
        if(user==null){
            return Result.fail("登录失败！");
        }
        if(!encode(password).equals(user.getPassword())){
            return Result.fail("登录失败！");
        }
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf(user.getId()));
        userDTO.setNickname(user.getNickname());
        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, filedValue)->filedValue.toString())
        );
        //设置token
        stringRedisTemplate.opsForHash().putAll(PREFIX_TOKEN+token,userMap);
        stringRedisTemplate.expire(PREFIX_TOKEN+token,TOKEN_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
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
            return Result.ok();
        }finally{
            stringRedisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Arrays.asList(PREFIX_REGISTER_PHONE+user.getPhone(), PREFIX_REGISTER_USERNAME+user.getUsername()),
                    lockValue
            );
        }
    }
}
