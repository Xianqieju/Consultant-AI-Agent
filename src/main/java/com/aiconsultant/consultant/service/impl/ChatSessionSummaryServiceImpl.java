package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.ChatSessionSummary;
import com.aiconsultant.consultant.mapper.ChatSessionSummaryMapper;
import com.aiconsultant.consultant.service.ChatSessionSummaryService;
import com.aiconsultant.consultant.service.MemorySummaryCacheService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
public class ChatSessionSummaryServiceImpl extends ServiceImpl<ChatSessionSummaryMapper, ChatSessionSummary>
        implements ChatSessionSummaryService {

    private final SnowflakeIdWorker snowflakeIdWorker;
    private final MemorySummaryCacheService memorySummaryCacheService;

    @Override
    public String loadSummaryTextFromDb(Long sessionId) {
        ChatSessionSummary row = getOne(new LambdaQueryWrapper<ChatSessionSummary>()
                .eq(ChatSessionSummary::getSessionId, sessionId));
        return row == null ? null : row.getSummaryText();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMergedSummary(Long sessionId, Long userId, String mergedText) {
        for (int attempt = 0; attempt < 5; attempt++) {
            ChatSessionSummary row = getOne(new LambdaQueryWrapper<ChatSessionSummary>()
                    .eq(ChatSessionSummary::getSessionId, sessionId));
            if (row == null) {
                try {
                    ChatSessionSummary insert = new ChatSessionSummary();
                    insert.setId(snowflakeIdWorker.nextId());
                    insert.setSessionId(sessionId);
                    insert.setUserId(userId);
                    insert.setSummaryText(mergedText);
                    insert.setVersion(0);
                    insert.setCreateTime(LocalDateTime.now());
                    insert.setUpdateTime(LocalDateTime.now());
                    if (save(insert)) {
                        registerInvalidate(sessionId);
                        return;
                    }
                } catch (DuplicateKeyException e) {
                    log.debug("并发插入会话摘要，重试更新 sessionId={}", sessionId);
                }
                continue;
            }
            row.setSummaryText(mergedText);
            row.setUpdateTime(LocalDateTime.now());
            if (updateById(row)) {
                registerInvalidate(sessionId);
                return;
            }
            log.warn("会话摘要乐观锁冲突，重试 attempt={} sessionId={}", attempt + 1, sessionId);
        }
        throw new IllegalStateException("会话摘要持久化失败（乐观锁重试耗尽） sessionId=" + sessionId);
    }

    private void registerInvalidate(Long sessionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                memorySummaryCacheService.invalidateSummary(sessionId);
            }
        });
    }
}
