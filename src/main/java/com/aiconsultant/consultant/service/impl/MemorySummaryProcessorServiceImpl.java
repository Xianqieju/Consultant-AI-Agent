package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.aiservice.MemorySummaryAgent;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.entity.MemorySummaryOutbox;
import com.aiconsultant.consultant.memory.MemoryOutboxStatus;
import com.aiconsultant.consultant.memory.MemoryProfileDefaults;
import com.aiconsultant.consultant.memory.MemoryTaskType;
import com.aiconsultant.consultant.mapper.ChatMessageMapper;
import com.aiconsultant.consultant.mapper.MemorySummaryOutboxMapper;
import com.aiconsultant.consultant.repository.RedisChatMemoryStore;
import com.aiconsultant.consultant.service.ChatSessionMemoryProfileService;
import com.aiconsultant.consultant.service.ChatSessionSummaryService;
import com.aiconsultant.consultant.service.MemorySummaryProcessorService;
import com.aiconsultant.consultant.service.AnswerFeedbackService;
import com.aiconsultant.consultant.service.UserMemoryProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemorySummaryProcessorServiceImpl implements MemorySummaryProcessorService {

    private final MemorySummaryOutboxMapper memorySummaryOutboxMapper;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final ChatSessionSummaryService chatSessionSummaryService;
    private final ChatSessionMemoryProfileService chatSessionMemoryProfileService;
    private final UserMemoryProfileService userMemoryProfileService;
    private final AnswerFeedbackService answerFeedbackService;
    private final ChatMessageMapper chatMessageMapper;
    private final MemorySummaryAgent memorySummaryAgent;
    private final ObjectMapper objectMapper;

    @Value("${app.memory.chat-window-size:20}")
    private int windowSize;
    @Value("${app.memory.summary-chunk-messages:10}")
    private int chunkMessages;

    @Override
    public void processOutbox(Long outboxId) {
        MemorySummaryOutbox outbox = memorySummaryOutboxMapper.selectById(outboxId);
        if (outbox == null) {
            log.warn("memory_summary_outbox 不存在 id={}", outboxId);
            return;
        }
        if (Objects.equals(outbox.getStatus(), MemoryOutboxStatus.DONE)) {
            return;
        }

        int claimed = memorySummaryOutboxMapper.update(null, new LambdaUpdateWrapper<MemorySummaryOutbox>()
                .eq(MemorySummaryOutbox::getId, outboxId)
                .eq(MemorySummaryOutbox::getStatus, MemoryOutboxStatus.PENDING)
                .set(MemorySummaryOutbox::getStatus, MemoryOutboxStatus.PROCESSING));
        if (claimed == 0) {
            log.debug("memory summary outbox 未领取（非 PENDING） id={}", outboxId);
            return;
        }

        Long sessionId = outbox.getSessionId();
        Long userId = outbox.getUserId();
        MemoryTaskType taskType = parseTaskType(outbox.getTaskType());

        try {
            String transcriptForProfile;
            if (taskType.includesSummary()) {
                List<dev.langchain4j.data.message.ChatMessage> all = redisChatMemoryStore.getMessages(sessionId);
                if (all == null || all.size() != windowSize) {
                    markFailed(outboxId, "redis_window_size=" + (all == null ? "null" : all.size()));
                    return;
                }
                if (chunkMessages > all.size()) {
                    markFailed(outboxId, "chunk_messages_exceeds_list");
                    return;
                }
                List<dev.langchain4j.data.message.ChatMessage> head = new ArrayList<>(all.subList(0, chunkMessages));
                String transcript = formatTranscript(head);
                transcriptForProfile = transcript;
                String existing = chatSessionSummaryService.loadSummaryTextFromDb(sessionId);
                if (existing == null) {
                    existing = "";
                }
                String merged = memorySummaryAgent.mergeSummary(existing, transcript);
                if (merged == null || merged.isBlank()) {
                    markFailed(outboxId, "empty_merge_result");
                    return;
                }
                chatSessionSummaryService.saveMergedSummary(sessionId, userId, merged.trim());

                List<dev.langchain4j.data.message.ChatMessage> tail = new ArrayList<>(all.subList(chunkMessages, windowSize));
                trimRedisWithRetries(sessionId, tail);
            } else {
                transcriptForProfile = buildTranscriptFromLatestMessages(sessionId, 20);
            }

            if (taskType.includesProfile()) {
                String sessionProfileJson = buildSessionProfileJson(transcriptForProfile);
                chatSessionMemoryProfileService.saveProfile(userId, sessionId, sessionProfileJson);
                userMemoryProfileService.mergeFromSessionProfile(userId, sessionProfileJson);
                String feedbackPatch = answerFeedbackService.buildPhrasePatchJson(userId, sessionId, 20);
                if (feedbackPatch != null && !feedbackPatch.isBlank()) {
                    userMemoryProfileService.mergeFromSessionProfile(userId, feedbackPatch);
                }
            }

            memorySummaryOutboxMapper.update(null, new LambdaUpdateWrapper<MemorySummaryOutbox>()
                    .eq(MemorySummaryOutbox::getId, outboxId)
                    .set(MemorySummaryOutbox::getStatus, MemoryOutboxStatus.DONE)
                    .set(MemorySummaryOutbox::getLastError, null));
            log.info("memory task 完成 outboxId={} sessionId={} taskType={}", outboxId, sessionId, taskType);
        } catch (Exception e) {
            log.error("memory task 处理异常 outboxId={} taskType={}", outboxId, taskType, e);
            markFailed(outboxId, truncate(e.getMessage(), 500));
        }
    }

    private void trimRedisWithRetries(Long sessionId, List<dev.langchain4j.data.message.ChatMessage> tail) {
        Exception last = null;
        for (int i = 0; i < 5; i++) {
            try {
                redisChatMemoryStore.updateMessages(sessionId, tail);
                return;
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(50L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
        log.error("Redis 裁剪记忆失败 sessionId={}", sessionId, last);
        throw new IllegalStateException("Redis 裁剪记忆失败 sessionId=" + sessionId, last);
    }

    private void markFailed(Long outboxId, String err) {
        memorySummaryOutboxMapper.update(null, new LambdaUpdateWrapper<MemorySummaryOutbox>()
                .eq(MemorySummaryOutbox::getId, outboxId)
                .set(MemorySummaryOutbox::getStatus, MemoryOutboxStatus.FAILED)
                .set(MemorySummaryOutbox::getLastError, err));
        log.warn("memory summary 标记失败 outboxId={} err={}", outboxId, err);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String formatTranscript(List<dev.langchain4j.data.message.ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage m : messages) {
            if (m instanceof UserMessage um) {
                sb.append("用户：").append(um.singleText()).append('\n');
            } else if (m instanceof AiMessage am) {
                sb.append("助手：").append(am.text()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private MemoryTaskType parseTaskType(String taskType) {
        try {
            return taskType == null ? MemoryTaskType.SUMMARY : MemoryTaskType.valueOf(taskType);
        } catch (Exception e) {
            return MemoryTaskType.SUMMARY;
        }
    }

    private String buildTranscriptFromLatestMessages(Long sessionId, int limit) {
        List<ChatMessage> latest = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getStatus, 0)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + limit));
        if (latest == null || latest.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = latest.size() - 1; i >= 0; i--) {
            ChatMessage m = latest.get(i);
            if (m.getRole() != null && m.getRole() == 0) {
                sb.append("用户：").append(m.getContent()).append('\n');
            } else if (m.getRole() != null && m.getRole() == 1) {
                sb.append("助手：").append(m.getContent()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String buildSessionProfileJson(String transcript) {
        ObjectNode profile = objectMapper.createObjectNode();
        profile.put("displayName", "");
        ArrayNode preferences = profile.putArray("preferences");
        ArrayNode topics = profile.putArray("topics");
        profile.putObject("ext");

        String t = transcript == null ? "" : transcript;
        if (t.contains("简洁") || t.contains("直接")) {
            preferences.add("简洁");
        }
        if (t.contains("详细") || t.contains("展开")) {
            preferences.add("详细");
        }
        if (t.contains("步骤") || t.contains("一步步")) {
            preferences.add("分步");
        }
        if (t.contains("代码")) {
            topics.add("代码");
        }
        if (t.contains("高考") || t.contains("志愿")) {
            topics.add("高考志愿");
        }
        if (t.contains("心理") || t.contains("焦虑")) {
            topics.add("心理支持");
        }

        if (preferences.isEmpty() && topics.isEmpty()) {
            return MemoryProfileDefaults.EMPTY_PROFILE_JSON;
        }
        return profile.toString();
    }
}
