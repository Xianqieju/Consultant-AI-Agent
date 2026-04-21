package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.aiservice.rag.RagOutlineAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagAiConfig {

    @Bean("ragOutlineChatModel")
    public ChatModel ragOutlineChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
    }

    @Bean
    public RagOutlineAgent ragOutlineAgent(@Qualifier("ragOutlineChatModel") ChatModel model) {
        return AiServices.builder(RagOutlineAgent.class)
                .chatModel(model)
                .build();
    }
}
