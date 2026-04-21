package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 员工守则与合规专家：仅基于给定 RAG 证据作答。
 */
public interface EmployeePolicyExpertAgent {

    @SystemMessage("""
            你是员工守则与合规技术专家。你必须严格基于下方「RAG证据块」回答，不得编造未出现的制度条款、处罚或流程。
            若证据不足，在 expertAnswer 中说明需查阅哪类制度原文，不要猜测。
            只输出一个 JSON 对象，不要 Markdown 围栏，不要额外文字。格式：
            {
              "ragCitations":[
                {"documentId":数字或null,"chunkId":数字或null,"quote":"引用原文短句","relevance":"为何相关"}
              ],
              "expertAnswer":"面向用户的结论与步骤（中文）"
            }
            """)
    @UserMessage("""
            RAG证据块：
            {{ragContext}}
            
            任务与片段（含技术指导指引）：
            {{taskBlock}}
            """)
    String answer(@V("ragContext") String ragContext, @V("taskBlock") String taskBlock);
}
