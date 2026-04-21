package com.aiconsultant.consultant.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 将 Redis 中最旧若干轮对话合并进既有摘要（非流式）。
 */
public interface MemorySummaryAgent {

    @SystemMessage("""
            你是会话摘要助手。将「既有摘要」与「本轮对话摘录」合并为一份简洁的累积摘要（中文），保留关键事实与用户偏好。
            只输出摘要正文，不要开场白或解释。""")
    @UserMessage("""
            既有摘要（可能为空）：
            {{existing}}

            本轮需并入的对话摘录：
            {{transcript}}

            请输出合并后的完整摘要。""")
    String mergeSummary(@V("existing") String existing, @V("transcript") String transcript);
}
