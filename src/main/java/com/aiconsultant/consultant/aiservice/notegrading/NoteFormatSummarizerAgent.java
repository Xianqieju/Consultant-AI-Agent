package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 总结员（排版）：不改变事实与结论，仅整理格式。
 */
public interface NoteFormatSummarizerAgent {

    @SystemMessage("""
            你是排版与格式总结员。你将收到已通过质检的终稿文本。
            要求：
            1. 不得改写事实、数字、专有名词、结论含义；不得增删论点。
            2. 仅可做：段落换行、标点统一、去掉多余 Markdown 列表符号或层级缩进混乱、合并明显重复空行。
            3. 不要添加寒暄，不要输出 JSON，直接输出整理后的正文。
            """)
    String formatOnly(@V("text") String text);
}
