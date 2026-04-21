package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.memory.MemoryRedisKeys;
import com.aiconsultant.consultant.service.MemorySummaryCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MemorySummaryCacheServiceImpl implements MemorySummaryCacheService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.memory.cache-ttl-hours:24}")
    private int cacheTtlHours;

    @Override
    public String getCachedSummaryText(Long sessionId) {
        return redisTemplate.opsForValue().get(MemoryRedisKeys.summaryCache(sessionId));
    }

    @Override
    public void putSummaryText(Long sessionId, String text) {
        if (text == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                MemoryRedisKeys.summaryCache(sessionId),
                text,
                Duration.ofHours(cacheTtlHours));
    }

    @Override
    public void invalidateSummary(Long sessionId) {
        redisTemplate.delete(MemoryRedisKeys.summaryCache(sessionId));
    }

    @Override
    public String getCachedProfileJson(Long userId, Long sessionId) {
        return redisTemplate.opsForValue().get(MemoryRedisKeys.profileCache(userId, sessionId));
    }

    @Override
    public void putProfileJson(Long userId, Long sessionId, String json) {
        if (json == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                MemoryRedisKeys.profileCache(userId, sessionId),
                json,
                Duration.ofHours(cacheTtlHours));
    }

    @Override
    public void invalidateProfile(Long userId, Long sessionId) {
        redisTemplate.delete(MemoryRedisKeys.profileCache(userId, sessionId));
        redisTemplate.delete(MemoryRedisKeys.profileSessionCache(sessionId));
    }

    @Override
    public String getCachedProfileJsonBySession(Long sessionId) {
        return redisTemplate.opsForValue().get(MemoryRedisKeys.profileSessionCache(sessionId));
    }

    @Override
    public void putProfileJsonBySession(Long sessionId, String json) {
        if (json == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                MemoryRedisKeys.profileSessionCache(sessionId),
                json,
                Duration.ofHours(cacheTtlHours));
    }

    @Override
    public void invalidateProfileSession(Long sessionId) {
        redisTemplate.delete(MemoryRedisKeys.profileSessionCache(sessionId));
    }

    @Override
    public String getCachedGlobalProfileJson(Long userId) {
        return redisTemplate.opsForValue().get(MemoryRedisKeys.globalProfileCache(userId));
    }

    @Override
    public void putGlobalProfileJson(Long userId, String json) {
        if (json == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                MemoryRedisKeys.globalProfileCache(userId),
                json,
                Duration.ofHours(cacheTtlHours));
    }

    @Override
    public void invalidateGlobalProfile(Long userId) {
        redisTemplate.delete(MemoryRedisKeys.globalProfileCache(userId));
    }
}
