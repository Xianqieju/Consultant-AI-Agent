package com.aiconsultant.consultant.memory;

import com.aiconsultant.consultant.repository.RedisChatMemoryStore;
import com.aiconsultant.consultant.service.ChatSessionMemoryProfileService;
import com.aiconsultant.consultant.service.ChatSessionSummaryService;
import com.aiconsultant.consultant.service.MemorySummaryCacheService;
import com.aiconsultant.consultant.service.UserMemoryProfileService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 与 {@code CasualChatAgent} 等 AiService 的 {@code chatMemoryProvider = "chatMemoryProvider"} 对齐。
 */
@Component("chatMemoryProvider")
@RequiredArgsConstructor
public class SessionChatMemoryProvider implements ChatMemoryProvider {

    private final RedisChatMemoryStore redisChatMemoryStore;
    private final MemorySummaryCacheService memorySummaryCacheService;
    private final ChatSessionSummaryService chatSessionSummaryService;
    private final ChatSessionMemoryProfileService chatSessionMemoryProfileService;
    private final UserMemoryProfileService userMemoryProfileService;

    @Value("${app.memory.chat-window-size:20}")
    private int chatWindowSize;

    @Override
    public ChatMemory get(Object memoryId) {
        long sessionId = (Long) memoryId;
        MessageWindowChatMemory base = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(chatWindowSize)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
        return new SummaryAwareChatMemory(
                base,
                sessionId,
                sid -> {
                    String cached = memorySummaryCacheService.getCachedSummaryText(sid);
                    if (cached != null && !cached.isBlank()) {
                        return cached;
                    }
                    String fromDb = chatSessionSummaryService.loadSummaryTextFromDb(sid);
                    if (fromDb != null && !fromDb.isBlank()) {
                        memorySummaryCacheService.putSummaryText(sid, fromDb);
                    }
                    return fromDb;
                },
                sid -> {
                    Long userId = chatSessionMemoryProfileService.findUserIdBySession(sid);
                    return userMemoryProfileService.getPhrasesForMemory(userId);
                },
                chatSessionMemoryProfileService::getProfileJsonForMemory);
    }
}
