package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.aiservice.MemorySummaryAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryAiConfig {

    @Bean("memorySummaryChatModel")
    public ChatModel memorySummaryChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${app.memory.summary-llm-temperature:0.2}") double temperature
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean
    public MemorySummaryAgent memorySummaryAgent(
            @org.springframework.beans.factory.annotation.Qualifier("memorySummaryChatModel") ChatModel model
    ) {
        return AiServices.builder(MemorySummaryAgent.class)
                .chatModel(model)
                .build();
    }
}
