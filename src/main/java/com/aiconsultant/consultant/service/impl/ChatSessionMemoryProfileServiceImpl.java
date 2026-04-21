package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.ChatSessionMemoryProfile;
import com.aiconsultant.consultant.mapper.ChatSessionMemoryProfileMapper;
import com.aiconsultant.consultant.memory.MemoryProfileDefaults;
import com.aiconsultant.consultant.service.ChatSessionMemoryProfileService;
import com.aiconsultant.consultant.service.MemorySummaryCacheService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionMemoryProfileServiceImpl extends ServiceImpl<ChatSessionMemoryProfileMapper, ChatSessionMemoryProfile>
        implements ChatSessionMemoryProfileService {

    private final SnowflakeIdWorker snowflakeIdWorker;
    private final MemorySummaryCacheService memorySummaryCacheService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProfileJson(Long userId, Long sessionId) {
        String bySession = memorySummaryCacheService.getCachedProfileJsonBySession(sessionId);
        if (bySession != null && !bySession.isBlank()) {
            return bySession;
        }
        String byUser = memorySummaryCacheService.getCachedProfileJson(userId, sessionId);
        if (byUser != null && !byUser.isBlank()) {
            return byUser;
        }
        ChatSessionMemoryProfile row = getOne(new LambdaQueryWrapper<ChatSessionMemoryProfile>()
                .eq(ChatSessionMemoryProfile::getUserId, userId)
                .eq(ChatSessionMemoryProfile::getSessionId, sessionId));
        if (row != null && row.getProfileJson() != null && !row.getProfileJson().isBlank()) {
            String j = row.getProfileJson();
            warmCaches(userId, sessionId, j);
            return j;
        }
        return MemoryProfileDefaults.EMPTY_PROFILE_JSON;
    }

    @Override
    public String getProfileJsonForMemory(Long sessionId) {
        String bySession = memorySummaryCacheService.getCachedProfileJsonBySession(sessionId);
        if (bySession != null && !MemoryProfileDefaults.isEffectivelyEmpty(bySession)) {
            return bySession;
        }
        ChatSessionMemoryProfile row = getOne(new LambdaQueryWrapper<ChatSessionMemoryProfile>()
                .eq(ChatSessionMemoryProfile::getSessionId, sessionId));
        if (row == null || row.getProfileJson() == null) {
            return null;
        }
        String j = row.getProfileJson();
        if (MemoryProfileDefaults.isEffectivelyEmpty(j)) {
            return null;
        }
        warmCaches(row.getUserId(), sessionId, j);
        return j;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProfile(Long userId, Long sessionId, String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            throw new IllegalArgumentException("profileJson 不能为空");
        }
        JsonNode n;
        try {
            n = objectMapper.readTree(profileJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("profileJson 必须是合法 JSON");
        }
        if (!n.isObject()) {
            throw new IllegalArgumentException("profileJson 必须是 JSON 对象");
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            ChatSessionMemoryProfile row = getOne(new LambdaQueryWrapper<ChatSessionMemoryProfile>()
                    .eq(ChatSessionMemoryProfile::getUserId, userId)
                    .eq(ChatSessionMemoryProfile::getSessionId, sessionId));
            if (row == null) {
                try {
                    ChatSessionMemoryProfile insert = new ChatSessionMemoryProfile();
                    insert.setId(snowflakeIdWorker.nextId());
                    insert.setUserId(userId);
                    insert.setSessionId(sessionId);
                    insert.setProfileJson(profileJson);
                    insert.setVersion(0);
                    insert.setCreateTime(LocalDateTime.now());
                    insert.setUpdateTime(LocalDateTime.now());
                    if (save(insert)) {
                        registerInvalidate(userId, sessionId);
                        return;
                    }
                } catch (DuplicateKeyException e) {
                    log.debug("并发插入会话画像，重试更新 userId={} sessionId={}", userId, sessionId);
                }
                continue;
            }
            row.setProfileJson(profileJson);
            row.setUpdateTime(LocalDateTime.now());
            if (updateById(row)) {
                registerInvalidate(userId, sessionId);
                return;
            }
            log.warn("会话画像乐观锁冲突，重试 attempt={} sessionId={}", attempt + 1, sessionId);
        }
        throw new IllegalStateException("会话画像持久化失败");
    }

    @Override
    public Long findUserIdBySession(Long sessionId) {
        ChatSessionMemoryProfile row = getOne(new LambdaQueryWrapper<ChatSessionMemoryProfile>()
                .eq(ChatSessionMemoryProfile::getSessionId, sessionId)
                .last("LIMIT 1"));
        return row == null ? null : row.getUserId();
    }

    private void warmCaches(Long userId, Long sessionId, String json) {
        memorySummaryCacheService.putProfileJson(userId, sessionId, json);
        memorySummaryCacheService.putProfileJsonBySession(sessionId, json);
    }

    private void registerInvalidate(Long userId, Long sessionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memorySummaryCacheService.invalidateProfile(userId, sessionId);
            }
        });
    }
}
