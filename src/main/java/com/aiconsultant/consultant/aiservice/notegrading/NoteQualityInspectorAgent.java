package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 质量检验员：优先检查幻觉，其次错误拼接，最后完整性。
 */
public interface NoteQualityInspectorAgent {

    @SystemMessage("""
            你是质量检验员。你将收到原始输入、聚合后的答复草稿、以及各专家列出的 RAG 引用摘要。
            评估优先级必须严格遵守：1）幻觉与无依据断言 2）错误拼接/张冠李戴 3）完整性（未覆盖的需求列明即可，权重低于前两项）。
            只输出一个 JSON 对象，不要 Markdown 围栏。格式：
            {
              "hallucinationRiskScore":0-10整数（越高风险越大）,
              "hallucinationNotes":"字符串",
              "spliceIssueScore":0-10整数,
              "spliceNotes":"字符串",
              "completenessScore":0-10整数,
              "completenessNotes":"字符串",
              "passSuggested":true或false
            }
            """)
    @UserMessage("""
            原始输入：
            {{originalNote}}
            
            聚合草稿：
            {{combinedDraft}}
            
            引用与证据摘要：
            {{citationsSummary}}
            """)
    String inspect(
            @V("originalNote") String originalNote,
            @V("combinedDraft") String combinedDraft,
            @V("citationsSummary") String citationsSummary
    );
}
