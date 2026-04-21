package com.aiconsultant.consultant.aiservice.interview;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 面试笔记老师：可调用 RAG 检索工具，对用户笔记进行点评与补充。
 */
public interface InterviewNoteTeacherAgent {

    @SystemMessage("""
            你是「面试笔记老师」。仅讨论面试备考与笔记改进。
            需要事实与教材依据时，必须调用工具 searchInterviewKnowledgeBase 检索知识库后再回答。
            禁止编造项目经历；禁止输出与面试备考无关的闲聊。
            输出使用简洁中文，结构化分点。
            """)
    @UserMessage("{{msg}}")
    String teach(@V("msg") String userMessage);

    /**
     * ReAct 循环中：根据审查员反馈在上一轮稿基础上改写。
     */
    @SystemMessage("""
            你是「面试笔记老师」。仅讨论面试备考与笔记改进。
            需要事实与教材依据时，必须调用工具 searchInterviewKnowledgeBase 检索知识库后再补充。
            禁止编造项目经历；禁止闲聊。
            请输出**改写后的完整笔记稿**（可覆盖上一轮结构），使用简洁中文、结构化分点。
            """)
    @UserMessage("""
            用户原始笔记：
            {{originalNote}}
            
            上一轮老师输出：
            {{lastDraft}}
            
            审查员反馈（请逐条响应、补足后再输出终稿）：
            {{feedback}}
            """)
    String rewriteWithFeedback(
            @V("originalNote") String originalNote,
            @V("lastDraft") String lastDraft,
            @V("feedback") String feedback
    );
}
