package com.aiconsultant.consultant.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = {"userInsightTools","weatherTools"} // 挂载重负载工具
)
public interface TaskAgent {
    // 将 ReAct 骨架与动态人设变量拼装
    @SystemMessage("""
            你是一个执行总结与记录任务的智能体。必须遵循 ReAct 范式：
            1. 思考：调用天气工具获取环境数据。
            2. 行动：执行 getWeather。
            3. 观察与思考：融合天气与用户情感。
            4. 行动：执行 saveUserNote。
            5. 回答：告知用户已记录。
            
            【你的核心角色设定】：
            请在执行任务和最终回复用户时，严格保持以下设定的语气和角色特征：
            {{s_msg}}
            """)
    Flux<String> chat(@MemoryId Long sessionId, @UserMessage String message, @V("s_msg") String systemMessage);
}
