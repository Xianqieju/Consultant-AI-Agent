package com.aiconsultant.consultant.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonBloomFilterConfig {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 用户 ID 布隆过滤器
     */
    @Bean
    public RBloomFilter<Long> userBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("bloom:user:id");
        // 初始化：预计 100 万用户，误差率 0.01%
        // 注意：tryInit 只有在过滤器尚未初始化时才会生效
        bloomFilter.tryInit(1000000L, 0.0001);
        return bloomFilter;
    }

    /**
     * 用户-会话 组合布隆过滤器 (UserId:SessionId)
     */
    @Bean
    public RBloomFilter<String> sessionBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:user:session");
        // 初始化：预计 500 万个对话，误差率 0.01%
        bloomFilter.tryInit(5000000L, 0.0001);
        return bloomFilter;
    }
}
