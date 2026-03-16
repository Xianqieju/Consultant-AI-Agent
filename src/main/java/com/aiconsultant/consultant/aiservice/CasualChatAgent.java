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
        chatMemoryProvider = "chatMemoryProvider"
        //contentRetriever = "contentRetriever" // 保留知识库检索能力
)
public interface CasualChatAgent {
    // 纯粹的动态人设注入
    @SystemMessage("{{s_msg}}")
    Flux<String> chat(@MemoryId Long sessionId, @UserMessage String message, @V("s_msg") String systemMessage);
}