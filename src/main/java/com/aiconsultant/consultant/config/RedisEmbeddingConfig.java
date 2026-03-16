//package com.itheima.consultant.config;
//
//import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RedisEmbeddingConfig {
//    @Bean
//    public RedisEmbeddingStore redisEmbeddingStore() {
//        return RedisEmbeddingStore.builder()
//                .host("localhost")
//                .user("default")
//                .port(6379)
//                .password("123") // 显式写入密码
//                .indexName("consultant-index")
//                .dimension(1536) // text-embedding-v3 维度
//                .build();
//    }
//}
