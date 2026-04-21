package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.config.RabbitMQConfig;
import com.aiconsultant.consultant.pojo.MemorySummaryMqPayload;
import com.aiconsultant.consultant.service.MemorySummaryProcessorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySummaryListener {

    private final ObjectMapper objectMapper;
    private final MemorySummaryProcessorService memorySummaryProcessorService;

    @RabbitListener(queues = RabbitMQConfig.MEMORY_SUMMARY_QUEUE, ackMode = "MANUAL")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String json = new String(message.getBody());
            MemorySummaryMqPayload payload = objectMapper.readValue(json, MemorySummaryMqPayload.class);
            if (payload.getOutboxId() != null) {
                memorySummaryProcessorService.processOutbox(payload.getOutboxId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("memory summary 消费异常", e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
