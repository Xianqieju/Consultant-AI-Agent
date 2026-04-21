package com.aiconsultant.consultant.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * 在底层滑动窗口记忆之上，按需注入「会话历史摘要 / 用户画像」系统消息块，供模型拼装上下文。
 */
public class SummaryAwareChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final long sessionId;
    private final LongFunction<String> summaryLoader;
    private final LongFunction<String> globalProfileLoader;
    private final LongFunction<String> profileLoader;

    public SummaryAwareChatMemory(ChatMemory delegate, long sessionId, LongFunction<String> summaryLoader) {
        this(delegate, sessionId, summaryLoader, null, null);
    }

    public SummaryAwareChatMemory(
            ChatMemory delegate,
            long sessionId,
            LongFunction<String> summaryLoader,
            LongFunction<String> globalProfileLoader,
            LongFunction<String> profileLoader) {
        this.delegate = delegate;
        this.sessionId = sessionId;
        this.summaryLoader = summaryLoader;
        this.globalProfileLoader = globalProfileLoader;
        this.profileLoader = profileLoader;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        delegate.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        String summary = summaryLoader == null ? null : summaryLoader.apply(sessionId);
        String globalProfile = globalProfileLoader == null ? null : globalProfileLoader.apply(sessionId);
        String profile = profileLoader == null ? null : profileLoader.apply(sessionId);
        String preamble = buildPreamble(summary, globalProfile, profile);
        List<ChatMessage> base = delegate.messages();
        if (preamble == null || preamble.isBlank()) {
            return base;
        }
        List<ChatMessage> merged = new ArrayList<>(base.size() + 1);
        merged.add(SystemMessage.systemMessage(preamble));
        merged.addAll(base);
        return merged;
    }

    private static String buildPreamble(String summary, String globalProfile, String profile) {
        boolean hasS = summary != null && !summary.isBlank();
        boolean hasG = globalProfile != null && !globalProfile.isBlank();
        boolean hasP = profile != null && !profile.isBlank();
        if (!hasS && !hasG && !hasP) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (hasS) {
            sb.append("【会话历史摘要】\n").append(summary.trim());
        }
        if (hasG) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("【用户级偏好短语】\n").append(globalProfile.trim());
        }
        if (hasP) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("【会话级偏好】\n").append(profile.trim());
        }
        return sb.toString();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
