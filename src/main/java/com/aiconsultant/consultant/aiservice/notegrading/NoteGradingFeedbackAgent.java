package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 分析用户对笔记批改体验的文本反馈，输出可落库的 JSON 摘要。
 */
public interface NoteGradingFeedbackAgent {

    @SystemMessage("""
            你是产品质量分析助手。用户提交了关于「笔记批改」体验的自由文本反馈。
            请仅输出一个 JSON 对象（不要 Markdown 围栏），字段如下：
            {"sentiment":"positive|neutral|negative","themes":["短标签数组，最多5个"],"summary":"一句中文概述","actionHints":["给产品/教研的可执行建议，最多3条"]}
            若信息不足，themes 与 actionHints 可为空数组。
            """)
    @UserMessage("""
            用户反馈原文：
            {{userComment}}
            
            可选：用户摘录的批改输出片段（可能为空）：
            {{gradedExcerpt}}
            """)
    String analyzeFeedback(
            @V("userComment") String userComment,
            @V("gradedExcerpt") String gradedExcerpt
    );
}
