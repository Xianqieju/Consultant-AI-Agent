package com.aiconsultant.consultant.listener;

import com.aiconsultant.consultant.config.RabbitMQConfig;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.pojo.ChatMessageDTO;
import com.aiconsultant.consultant.service.ChatMessageService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class ChatListner {
    @Autowired
    ChatMessageService chatMessageService;

    @RabbitListener(queues = RabbitMQConfig.CHAT_QUEUE)
    public void saveChatHistory(ChatMessageDTO messageDTO, Channel channel, Message message)throws Exception {
        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setId(messageDTO.getId());

            chatMessage.setUserId(messageDTO.getUserId());
            chatMessage.setSessionId(messageDTO.getSessionId());
            chatMessage.setRole(messageDTO.getRole());
            chatMessage.setContent(messageDTO.getContent());
            chatMessage.setCorrelationId(messageDTO.getCorrelationId());
            chatMessage.setStatus(messageDTO.getStatus());
            chatMessage.setCreateTime(LocalDateTime.now());

            // 执行落库
            log.info("即将插入的数据内容：{}", chatMessage);
            log.info("尝试投递");
            boolean success = chatMessageService.saveOrUpdate(chatMessage);
            log.info("消息处理完毕, ID: {}, 结果: {}", chatMessage.getId(), success ? "成功(插入或更新)" : "失败");
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("异步保存聊天记录失败, userId: {}, error: {}", messageDTO.getUserId(), e.getMessage());
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            // 视业务要求，可在此处抛出异常触发 MQ 重试机制，或写入死信队列
        }
    }
}
