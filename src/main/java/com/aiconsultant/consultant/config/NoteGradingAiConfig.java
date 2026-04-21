package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.aiservice.notegrading.DevGuideExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.EmployeePolicyExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.InternalNetworkExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteAggregatorAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteFormatSummarizerAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteGradingFallbackSummarizerAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteGradingFeedbackAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteQualityInspectorAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteSupervisorAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteTechnicalGuideAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多智能体笔记批改：专家使用低温度；质检略低；排版极低；技术指导低温度。
 */
@Configuration
public class NoteGradingAiConfig {

    private static OpenAiChatModel baseModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean("noteGradingExpertChatModel")
    public ChatModel noteGradingExpertChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.12);
    }

    @Bean("noteGradingSupervisorChatModel")
    public ChatModel noteGradingSupervisorChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.25);
    }

    @Bean("noteGradingAggregatorChatModel")
    public ChatModel noteGradingAggregatorChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.35);
    }

    @Bean("noteGradingFallbackChatModel")
    public ChatModel noteGradingFallbackChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.22);
    }

    @Bean("noteGradingFeedbackChatModel")
    public ChatModel noteGradingFeedbackChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.2);
    }

    @Bean("noteGradingTechGuideChatModel")
    public ChatModel noteGradingTechGuideChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.2);
    }

    @Bean("noteGradingQualityChatModel")
    public ChatModel noteGradingQualityChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.15);
    }

    @Bean("noteGradingFormatChatModel")
    public ChatModel noteGradingFormatChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return baseModel(baseUrl, apiKey, modelName, 0.05);
    }

    @Bean
    public NoteSupervisorAgent noteSupervisorAgent(
            @Qualifier("noteGradingSupervisorChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteSupervisorAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteTechnicalGuideAgent noteTechnicalGuideAgent(
            @Qualifier("noteGradingTechGuideChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteTechnicalGuideAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public InternalNetworkExpertAgent internalNetworkExpertAgent(
            @Qualifier("noteGradingExpertChatModel") ChatModel model
    ) {
        return AiServices.builder(InternalNetworkExpertAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public DevGuideExpertAgent devGuideExpertAgent(
            @Qualifier("noteGradingExpertChatModel") ChatModel model
    ) {
        return AiServices.builder(DevGuideExpertAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public EmployeePolicyExpertAgent employeePolicyExpertAgent(
            @Qualifier("noteGradingExpertChatModel") ChatModel model
    ) {
        return AiServices.builder(EmployeePolicyExpertAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteQualityInspectorAgent noteQualityInspectorAgent(
            @Qualifier("noteGradingQualityChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteQualityInspectorAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteFormatSummarizerAgent noteFormatSummarizerAgent(
            @Qualifier("noteGradingFormatChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteFormatSummarizerAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteAggregatorAgent noteAggregatorAgent(
            @Qualifier("noteGradingAggregatorChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteAggregatorAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteGradingFallbackSummarizerAgent noteGradingFallbackSummarizerAgent(
            @Qualifier("noteGradingFallbackChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteGradingFallbackSummarizerAgent.class)
                .chatModel(model)
                .build();
    }

    @Bean
    public NoteGradingFeedbackAgent noteGradingFeedbackAgent(
            @Qualifier("noteGradingFeedbackChatModel") ChatModel model
    ) {
        return AiServices.builder(NoteGradingFeedbackAgent.class)
                .chatModel(model)
                .build();
    }
}
