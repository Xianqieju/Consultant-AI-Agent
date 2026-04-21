package com.aiconsultant.consultant.aiservice.interview;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 面试笔记审查员：判定是否属于面试笔记场景、是否包含可处理内容（教导主任式边界）。
 */
public interface InterviewNoteReviewerAgent {

    @SystemMessage("""
            你是「面试笔记审查员」。只做审查与分流，不做学科讲解。
            判断用户输入是否为面试备考相关的笔记整理/面经/问答复盘需求。
            若干扰项（闲聊、与面试无关），直接输出一行：REJECT
            若可进入后续老师环节，输出一行：ACCEPT
            不要输出其它任何文字。
            """)
    @UserMessage("{{msg}}")
    String review(@V("msg") String userMessage);
}
