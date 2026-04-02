package com.aiconsultant.consultant.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 充值订单超时取消：消息进入 TTL 队列，15 分钟后过期进入死信交换机，由取消队列消费。
 */
@Configuration
public class RabbitRechargeOrderDelayConfig {

    /** 15 分钟，毫秒（队列级 x-message-ttl） */
    public static final int ORDER_CANCEL_DELAY_MS = 15 * 60 * 1000;

    public static final String ORDER_TTL_QUEUE = "recharge.order.ttl.queue";
    public static final String ORDER_CANCEL_EXCHANGE = "recharge.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "recharge.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "recharge.cancel";

    @Bean
    public DirectExchange rechargeCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    @Bean
    public Queue rechargeCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding rechargeCancelBinding() {
        return BindingBuilder.bind(rechargeCancelQueue())
                .to(rechargeCancelExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }

    /**
     * TTL 队列：消息停留 15 分钟后过期，转入死信交换机 {@link #ORDER_CANCEL_EXCHANGE}。
     */
    @Bean
    public Queue rechargeOrderTtlQueue() {
        return QueueBuilder.durable(ORDER_TTL_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY)
                .withArgument("x-message-ttl", ORDER_CANCEL_DELAY_MS)
                .build();
    }

    // 默认交换机隐式按队列名路由；使用 rabbitTemplate.convertAndSend("", ORDER_TTL_QUEUE, body) 即可投递
}
