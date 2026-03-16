package com.aiconsultant.consultant.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class DlqConfig {

    // 死信交换机
    public static final String DLX_EXCHANGE = "dlx.exchange";
    // 死信队列
    public static final String DLX_QUEUE = "dlx.queue";
    // 死信路由键
    public static final String DLX_ROUTING_KEY = "dlx.key";

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE, true);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    @Bean
    public Queue businessQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置死信交换机
        args.put("x-dead-letter-exchange", DlqConfig.DLX_EXCHANGE);
        // 设置死信路由键（可选，如果不设置则使用原有的路由键）
        args.put("x-dead-letter-routing-key", DlqConfig.DLX_ROUTING_KEY);

        // 设置消息有效期（可选，例如10秒过期进入死信）
        // args.put("x-message-ttl", 10000);

        return new Queue("business.queue", true, false, false, args);
    }
}
