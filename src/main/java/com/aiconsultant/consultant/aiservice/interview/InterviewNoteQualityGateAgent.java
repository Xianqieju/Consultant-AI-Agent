package com.aiconsultant.consultant.aiservice.interview;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 质量闸门：对老师当前稿打分（详细度/完整性），供 ReAct 循环决定是否回退改写。
 * 仅输出 JSON，由编排层解析；是否「达标」最终以配置阈值在 Java 侧判定。
 */
public interface InterviewNoteQualityGateAgent {

    @SystemMessage("""
            你是「面试笔记质量审查员」。只评价当前稿相对用户原始笔记的**详细度与完整性**（考点是否展开、是否有可落地的要点/追问方向），不做全文重写。
            你不调用任何检索或外部工具：仅对比「用户原始笔记」与「当前老师稿」做快速核对与打分，以保证低延迟。
            严格只输出一个 JSON 对象，不要 Markdown 围栏，不要其它文字。字段：
            {"score":整数0到10,"feedback":"若分数偏低，列出最需要补充的3条以内要点；若已充分可写\"已通过\""}
            评分参考：0-3 过于简略或离题；4-6 有框架缺细节；7-8 较完整；9-10 充分可备考复盘。
            """)
    @UserMessage("""
            用户原始笔记：
            {{originalNote}}
            
            当前老师稿：
            {{currentDraft}}
            """)
    String scoreDraft(@V("originalNote") String originalNote, @V("currentDraft") String currentDraft);
}
