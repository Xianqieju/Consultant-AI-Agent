package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.UserMemoryProfile;
import com.aiconsultant.consultant.mapper.UserMemoryProfileMapper;
import com.aiconsultant.consultant.service.MemorySummaryCacheService;
import com.aiconsultant.consultant.service.UserMemoryProfileService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryProfileServiceImpl implements UserMemoryProfileService {

    private final UserMemoryProfileMapper userMemoryProfileMapper;
    private final MemorySummaryCacheService memorySummaryCacheService;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ObjectMapper objectMapper;

    @Value("${app.memory.profile.user-phrases-max-total:12}")
    private int maxTotal;
    @Value("${app.memory.profile.user-phrases-max-per-bucket:4}")
    private int maxPerBucket;

    @Override
    public String getPhrasesForMemory(Long userId) {
        if (userId == null) {
            return null;
        }
        String cached = memorySummaryCacheService.getCachedGlobalProfileJson(userId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        UserMemoryProfile row = userMemoryProfileMapper.selectOne(new LambdaQueryWrapper<UserMemoryProfile>()
                .eq(UserMemoryProfile::getUserId, userId)
                .last("LIMIT 1"));
        if (row == null || row.getProfilePhrasesJson() == null || row.getProfilePhrasesJson().isBlank()) {
            return null;
        }
        String json = row.getProfilePhrasesJson();
        memorySummaryCacheService.putGlobalProfileJson(userId, json);
        return json;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeFromSessionProfile(Long userId, String sessionProfileJson) {
        if (userId == null || sessionProfileJson == null || sessionProfileJson.isBlank()) {
            return;
        }
        ObjectNode incoming = parseAsObject(sessionProfileJson);
        if (incoming == null) {
            return;
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            UserMemoryProfile row = userMemoryProfileMapper.selectOne(new LambdaQueryWrapper<UserMemoryProfile>()
                    .eq(UserMemoryProfile::getUserId, userId)
                    .last("LIMIT 1"));
            if (row == null) {
                try {
                    ObjectNode base = defaultPhraseProfile();
                    mergeBucket(base, incoming, "tone");
                    mergeBucket(base, incoming, "depth");
                    mergeBucket(base, incoming, "topics");
                    mergeBucket(base, incoming, "avoid");

                    UserMemoryProfile insert = new UserMemoryProfile();
                    insert.setId(snowflakeIdWorker.nextId());
                    insert.setUserId(userId);
                    insert.setProfilePhrasesJson(base.toString());
                    insert.setVersion(0);
                    insert.setCreateTime(LocalDateTime.now());
                    insert.setUpdateTime(LocalDateTime.now());
                    userMemoryProfileMapper.insert(insert);
                    registerInvalidate(userId);
                    return;
                } catch (DuplicateKeyException e) {
                    log.debug("并发插入用户级画像冲突，重试 userId={}", userId);
                }
                continue;
            }

            ObjectNode base = parseAsObject(row.getProfilePhrasesJson());
            if (base == null) {
                base = defaultPhraseProfile();
            }
            mergeBucket(base, incoming, "tone");
            mergeBucket(base, incoming, "depth");
            mergeBucket(base, incoming, "topics");
            mergeBucket(base, incoming, "avoid");
            row.setProfilePhrasesJson(base.toString());
            row.setUpdateTime(LocalDateTime.now());
            if (userMemoryProfileMapper.updateById(row) > 0) {
                registerInvalidate(userId);
                return;
            }
        }
        throw new IllegalStateException("用户级短语画像更新失败 userId=" + userId);
    }

    private void registerInvalidate(Long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memorySummaryCacheService.invalidateGlobalProfile(userId);
            }
        });
    }

    private ObjectNode parseAsObject(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            return n != null && n.isObject() ? (ObjectNode) n : null;
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode defaultPhraseProfile() {
        ObjectNode n = objectMapper.createObjectNode();
        n.putArray("tone");
        n.putArray("depth");
        n.putArray("topics");
        n.putArray("avoid");
        n.putObject("ext");
        return n;
    }

    private void mergeBucket(ObjectNode base, ObjectNode incoming, String key) {
        Set<String> merged = new LinkedHashSet<>();
        JsonNode oldArr = base.path(key);
        if (oldArr.isArray()) {
            for (JsonNode n : oldArr) {
                if (n.isTextual()) {
                    merged.add(normalizePhrase(n.asText()));
                }
            }
        }
        JsonNode inArr = incoming.path(key);
        if (inArr.isArray()) {
            for (JsonNode n : inArr) {
                if (n.isTextual()) {
                    merged.add(normalizePhrase(n.asText()));
                }
            }
        }
        ArrayNode out = objectMapper.createArrayNode();
        int used = 0;
        int total = 0;
        for (String p : merged) {
            if (p.isBlank()) {
                continue;
            }
            if (used >= maxPerBucket || total >= maxTotal) {
                break;
            }
            out.add(p);
            used++;
            total++;
        }
        base.set(key, out);
    }

    private static String normalizePhrase(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", "").trim();
        if (t.length() > 12) {
            return t.substring(0, 12);
        }
        return t;
    }
}
