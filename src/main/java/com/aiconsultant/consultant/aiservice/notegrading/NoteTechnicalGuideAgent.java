package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 技术指导：抽取待处理原文，并将需求拆解为可并行专家任务。
 */
public interface NoteTechnicalGuideAgent {

    @SystemMessage("""
            你是技术指导（严格角色扮演模式）。
            你的唯一任务：从用户输入中提取“要被处理的技术说明/笔记/问题原文”。
            
            要求：
            1. 若用户明确提出要处理、润色、补充、纠错某段内容，请抽取其中实际正文。
            2. 若用户只是在闲聊/问问题，但没有提供可处理正文，输出占位符：[NO_CONTENT_MATCH]。
            3. 只输出正文本身；不要输出任何额外解释、标题、Markdown 围栏或 JSON。
            """)
    String extractNote(@UserMessage String userMessage);

    @SystemMessage("""
            你是技术指导，负责判断需要哪些技术专家参与，并拆解为可执行任务，同时给出分段评分指导。
            请基于输入内容输出一个 JSON 对象，不要输出任何额外解释。格式如下：
            {
              "routingReason":"一句话说明为何选择这些专家",
              "globalConstraints":"全局约束，可空字符串",
              "overallScore":0-10整数,
              "overallScoreReason":"总分原因",
              "tasks":[
                {
                  "sectionId":"片段唯一ID，如 sec-1",
                  "domain":"INTERNAL_NETWORK|DEV_GUIDE|EMPLOYEE_POLICY|GENERAL",
                  "sourceText":"该片段原文",
                  "targetGoal":"该片段处理目标",
                  "scoreHint":0-10整数（越低表示越需重点修复）,
                  "reason":"评分理由",
                  "rewriteHint":"给对应专家的明确指引"
                }
              ]
            }
            规则：
            1) tasks 至少1项；若无法识别领域，domain 使用 GENERAL。
            2) sectionId 必须唯一；sourceText 必须来自原文，不得杜撰。
            3) 仅输出 JSON，不要 Markdown 围栏。
            """)
    @UserMessage("用户完整输入：\n{{note}}")
    String planAndScore(@V("note") String fullNote);
}
