package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 降级总结员：主批改链路失败时，将用户笔记整理为结构化要点稿（非专科逐项批注）。
 */
public interface NoteGradingFallbackSummarizerAgent {

    @SystemMessage("""
            你是学习笔记整理编辑。用户笔记的「专科批改」链路因服务异常未能完成。
            你的任务：仅基于用户提供的原始笔记与可选的中间片段摘要，输出一份**结构化整理稿**。
            要求：
            1. 使用 Markdown，固定包含以下小节标题（无内容则写「无」）：## 概要  ## 学科相关要点（分条）  ## 建议自查 / 待澄清
            2. 不要编造用户未写过的具体知识点细节；可做粗粒度归纳与分段。
            3. 不要输出 JSON；不要与用户寒暄；不要自称批改老师。
            4. 明确这是「整理稿」，不是正式批改结论。
            """)
    @UserMessage("""
            原始笔记：
            {{originalNote}}
            
            可选的中间片段摘要（可能为空）：
            {{poolContextSummary}}
            
            最后一次错误摘要（可为空）：
            {{lastErrorMessage}}
            """)
    String summarizeStructured(
            @V("originalNote") String originalNote,
            @V("poolContextSummary") String poolContextSummary,
            @V("lastErrorMessage") String lastErrorMessage
    );
}
