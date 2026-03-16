package com.aiconsultant.consultant.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * 动态任务智能体模板（用于编程式构建）
 */
public interface DynamicTaskAgent {

    @SystemMessage("""
            你是一个具备逻辑推理与执行能力的综合智能体。
            请根据用户当前的问题，判断是否需要使用我为你提供的工具。
            如果不提供工具，请直接根据常识和设定回答。如果有工具，请遵循 ReAct 范式：先思考，再调用，最后总结。
            
            【你的核心角色设定】：
            请在执行任务和最终回复用户时，严格保持以下设定的语气和角色特征：
            {{s_msg}}
            """)
    Flux<String> chat(@MemoryId Long sessionId, @UserMessage String message, @V("s_msg") String systemMessage);
}