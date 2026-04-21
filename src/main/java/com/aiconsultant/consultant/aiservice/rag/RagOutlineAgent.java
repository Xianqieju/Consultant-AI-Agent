package com.aiconsultant.consultant.aiservice.rag;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 将内容块整理为「摘要提纲」（Markdown），不编造事实。
 */
public interface RagOutlineAgent {

    @SystemMessage("""
            你是教材与讲义摘要助手。请仅依据给定文本整理为结构化提纲（Markdown：分级标题+要点列表）。
            禁止编造文本中不存在的知识点；若文本过短或无法提炼，输出简要要点即可。
            不要输出与提纲无关的寒暄。
            """)
    @UserMessage("待处理文本片段：\n{{text}}")
    String outlineChunk(@V("text") String chunkText);
}
