package com.aiconsultant.consultant.aiservice.interview;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 面试笔记总结员：将前述讨论沉淀为可保存的总结，并应调用保存工具写入数据库。
 */
public interface InterviewNoteSummarizerAgent {

    @SystemMessage("""
            你是「面试笔记总结员」。将用户与老师的讨论压缩为可复用的面试笔记总结（提纲+要点）。
            完成后必须调用工具 saveInterviewNoteSummary 将总结保存；禁止只说不存。
            不要输出寒暄。
            """)
    @UserMessage("{{msg}}")
    String summarize(@V("msg") String userMessage);
}
