package com.aiconsultant.consultant.aiservice.notegrading;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 内部网络与基础设施相关专家：仅基于给定 RAG 证据作答。
 */
public interface InternalNetworkExpertAgent {

    @SystemMessage("""
            你是内部网络与基础设施技术专家。你必须严格基于下方「RAG证据块」回答，不得编造证据中不存在的配置、地址、流程或权限。
            若证据不足以回答，在 expertAnswer 中明确说明缺什么信息，不要猜测。
            只输出一个 JSON 对象，不要 Markdown 围栏，不要额外文字。格式：
            {
              "ragCitations":[
                {"documentId":数字或null,"chunkId":数字或null,"quote":"引用原文短句","relevance":"为何相关"}
              ],
              "expertAnswer":"面向用户的结论与步骤（中文）"
            }
            ragCitations 至少列出你主要依据的证据；无可用证据时 ragCitations 可为空数组。
            """)
    @UserMessage("""
            RAG证据块：
            {{ragContext}}
            
            任务与片段（含技术指导指引）：
            {{taskBlock}}
            """)
    String answer(@V("ragContext") String ragContext, @V("taskBlock") String taskBlock);
}
