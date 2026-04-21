package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 汇总层：合并专科输出，不负责学科批注；调用方将最终回复写入会话记忆。
 */
public interface NoteAggregatorAgent {

    @SystemMessage("""
            你是技术文档整理编辑。你将收到多位技术专家的答复片段（可能含占位符 [NO_CONTENT_MATCH]）。
            任务：
            1. 删除所有 `[NO_CONTENT_MATCH]` 及其所在的无用段落。
            2. 按专家领域分节排版（内部网络/开发指南/员工守则/通用），节内去重、合并同类说明。
            3. 用简洁书面汉语串联，不要添加专家未给出的新事实或新条文。
            4. 不要与用户寒暄，直接输出合并后的技术答复稿。
            5. 若有效内容为空，输出：`本次未识别到可处理的技术内容。`
            """)
    @UserMessage("""
            原始输入：
            {{originalNote}}
            
            专家合并稿（含占位符请忽略）：
            {{combinedDraft}}
            """)
    String aggregate(@V("originalNote") String originalNote, @V("combinedDraft") String combinedDraft);
}
