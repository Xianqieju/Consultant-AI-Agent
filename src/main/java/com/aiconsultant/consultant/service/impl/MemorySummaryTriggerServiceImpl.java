package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.config.RabbitMQConfig;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.entity.MemorySummaryOutbox;
import com.aiconsultant.consultant.mapper.ChatMessageMapper;
import com.aiconsultant.consultant.mapper.MemorySummaryOutboxMapper;
import com.aiconsultant.consultant.memory.MemoryOutboxStatus;
import com.aiconsultant.consultant.memory.MemoryTaskType;
import com.aiconsultant.consultant.pojo.MemorySummaryMqPayload;
import com.aiconsultant.consultant.repository.RedisChatMemoryStore;
import com.aiconsultant.consultant.service.MemorySummaryTriggerService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemorySummaryTriggerServiceImpl implements MemorySummaryTriggerService {

    private final ChatMessageMapper chatMessageMapper;
    private final MemorySummaryOutboxMapper memorySummaryOutboxMapper;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.memory.chat-window-size:20}")
    private int chatWindowSize;
    @Value("${app.memory.summary-trigger-user-every:5}")
    private int triggerUserEvery;
    @Value("${app.memory.profile.session-update-every-user-messages:10}")
    private int profileUpdateEvery;
    @Value("${app.memory.profile.session-min-user-messages:10}")
    private int profileMinMessages;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tryEnqueueAfterAssistantReply(Long sessionId, Long userId) {
        long userMsgCount = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getRole, 0)
                .eq(ChatMessage::getStatus, 0));

        List<dev.langchain4j.data.message.ChatMessage> mem = redisChatMemoryStore.getMessages(sessionId);
        boolean summaryReady = userMsgCount > 0
                && userMsgCount % triggerUserEvery == 0
                && mem != null
                && mem.size() == chatWindowSize;
        boolean profileReady = userMsgCount >= profileMinMessages
                && userMsgCount % profileUpdateEvery == 0;
        MemoryTaskType taskType = resolveTaskType(summaryReady, profileReady);
        if (taskType == null) {
            return;
        }

        long inflight = memorySummaryOutboxMapper.selectCount(new LambdaQueryWrapper<MemorySummaryOutbox>()
                .eq(MemorySummaryOutbox::getSessionId, sessionId)
                .in(MemorySummaryOutbox::getStatus, MemoryOutboxStatus.PENDING, MemoryOutboxStatus.PROCESSING));
        if (inflight > 0) {
            return;
        }

        MemorySummaryOutbox row = new MemorySummaryOutbox();
        row.setId(snowflakeIdWorker.nextId());
        row.setSessionId(sessionId);
        row.setUserId(userId);
        row.setTaskType(taskType.name());
        row.setStatus(MemoryOutboxStatus.PENDING);
        row.setRetryCount(0);
        memorySummaryOutboxMapper.insert(row);

        MemorySummaryMqPayload payload = new MemorySummaryMqPayload(row.getId(), sessionId, userId, taskType.name());
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("memory summary payload 序列化失败", e);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.MEMORY_EXCHANGE,
                            RabbitMQConfig.MEMORY_SUMMARY_ROUTING_KEY,
                            json);
                    log.info("已投递 memory summary 发件箱 outboxId={} sessionId={}", row.getId(), sessionId);
                } catch (Exception e) {
                    log.error("memory summary MQ 投递失败 outboxId={}", row.getId(), e);
                }
            }
        });
    }

    private static MemoryTaskType resolveTaskType(boolean summaryReady, boolean profileReady) {
        if (summaryReady && profileReady) {
            return MemoryTaskType.BOTH;
        }
        if (summaryReady) {
            return MemoryTaskType.SUMMARY;
        }
        if (profileReady) {
            return MemoryTaskType.PROFILE;
        }
        return null;
    }
}
