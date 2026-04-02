package com.aiconsultant.consultant.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitRechargeConfig {

    public static final String RECHARGE_EXCHANGE = "recharge.exchange";
    public static final String RECHARGE_QUEUE = "recharge.queue";
    public static final String RECHARGE_ROUTING_KEY = "recharge.success";

    public static final String DLX_EXCHANGE = "recharge.dlx.exchange";
    public static final String DLX_QUEUE = "recharge.dlx.queue";
    public static final String DLX_ROUTING_KEY = "recharge.dlx.routing";

    // 1. 死信交换机与队列
    @Bean
    public DirectExchange rechargeDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue rechargeDlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding rechargeDlxBinding() {
        return BindingBuilder.bind(rechargeDlxQueue()).to(rechargeDlxExchange()).with(DLX_ROUTING_KEY);
    }

    // 2. 业务交换机与队列 (绑定死信参数)
    @Bean
    public DirectExchange rechargeExchange() {
        return new DirectExchange(RECHARGE_EXCHANGE);
    }

    @Bean
    public Queue rechargeQueue() {
        return QueueBuilder.durable(RECHARGE_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding rechargeBinding() {
        return BindingBuilder.bind(rechargeQueue()).to(rechargeExchange()).with(RECHARGE_ROUTING_KEY);
    }
}