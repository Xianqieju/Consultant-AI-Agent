package com.aiconsultant.consultant.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 队列名称
    public static final String CHAT_QUEUE = "chat.history.queue";
    // 交换机名称
    public static final String CHAT_EXCHANGE = "chat.exchange";
    // 路由键
    public static final String CHAT_ROUTING_KEY = "chat.history.routing";

    /** 会话摘要异步任务 */
    public static final String MEMORY_EXCHANGE = "memory.exchange";
    public static final String MEMORY_SUMMARY_QUEUE = "memory.summary.queue";
    public static final String MEMORY_SUMMARY_ROUTING_KEY = "memory.summary.routing";

    /**
     * 1. 声明队列
     * name: 队列名称
     * durable: 是否持久化（true表示MQ重启后队列依然存在）
     */
    @Bean
    public Queue chatHistoryQueue() {
        return new Queue(CHAT_QUEUE, true);
    }

    /**
     * 2. 声明交换机 (使用 Topic 类型)
     * name: 交换机名称
     * durable: 是否持久化
     * autoDelete: 是否自动删除
     */
    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(CHAT_EXCHANGE, true, false);
    }

    /**
     * 3. 绑定队列到交换机，并指定路由键
     */
    @Bean
    public Binding bindingChatHistory(Queue chatHistoryQueue, TopicExchange chatExchange) {
        return BindingBuilder
                .bind(chatHistoryQueue)
                .to(chatExchange)
                .with(CHAT_ROUTING_KEY);
    }

    @Bean
    public Queue memorySummaryQueue() {
        return new Queue(MEMORY_SUMMARY_QUEUE, true);
    }

    @Bean
    public TopicExchange memoryExchange() {
        return new TopicExchange(MEMORY_EXCHANGE, true, false);
    }

    @Bean
    public Binding bindingMemorySummary(Queue memorySummaryQueue, TopicExchange memoryExchange) {
        return BindingBuilder
                .bind(memorySummaryQueue)
                .to(memoryExchange)
                .with(MEMORY_SUMMARY_ROUTING_KEY);
    }

    /**
     * 4. 关键：配置消息转换器
     * 默认发送对象会使用 JDK 序列化（乱码且效率低），
     * 配置此 Bean 后，RabbitTemplate 会自动将 ChatMessageDTO 转为 JSON 字符串发送。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}