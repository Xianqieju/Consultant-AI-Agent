package com.aiconsultant.consultant.config;

import com.aiconsultant.consultant.aiservice.interview.InterviewNoteQualityGateAgent;
import com.aiconsultant.consultant.aiservice.interview.InterviewNoteReviewerAgent;
import com.aiconsultant.consultant.aiservice.interview.InterviewNoteSummarizerAgent;
import com.aiconsultant.consultant.aiservice.interview.InterviewNoteTeacherAgent;
import com.aiconsultant.consultant.tools.InterviewNoteSaveTools;
import com.aiconsultant.consultant.tools.InterviewRagTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 面试笔记三角色：审查员 / 老师（RAG 工具）/ 总结员（落库工具）。
 */
@Configuration
public class InterviewNoteAiConfig {

    private static OpenAiChatModel model(
            String baseUrl, String apiKey, String modelName, double temperature
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean("interviewReviewerChatModel")
    public ChatModel interviewReviewerChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return model(baseUrl, apiKey, modelName, 0.15);
    }

    @Bean("interviewTeacherChatModel")
    public ChatModel interviewTeacherChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return model(baseUrl, apiKey, modelName, 0.25);
    }

    @Bean("interviewSummarizerChatModel")
    public ChatModel interviewSummarizerChatModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName
    ) {
        return model(baseUrl, apiKey, modelName, 0.2);
    }

    @Bean
    public InterviewNoteReviewerAgent interviewNoteReviewerAgent(
            @Qualifier("interviewReviewerChatModel") ChatModel chatModel
    ) {
        return AiServices.builder(InterviewNoteReviewerAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public InterviewNoteQualityGateAgent interviewNoteQualityGateAgent(
            @Qualifier("interviewReviewerChatModel") ChatModel chatModel
    ) {
        return AiServices.builder(InterviewNoteQualityGateAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public InterviewNoteTeacherAgent interviewNoteTeacherAgent(
            @Qualifier("interviewTeacherChatModel") ChatModel chatModel,
            InterviewRagTools interviewRagTools
    ) {
        return AiServices.builder(InterviewNoteTeacherAgent.class)
                .chatModel(chatModel)
                .tools(interviewRagTools)
                .build();
    }

    @Bean
    public InterviewNoteSummarizerAgent interviewNoteSummarizerAgent(
            @Qualifier("interviewSummarizerChatModel") ChatModel chatModel,
            InterviewNoteSaveTools interviewNoteSaveTools
    ) {
        return AiServices.builder(InterviewNoteSummarizerAgent.class)
                .chatModel(chatModel)
                .tools(interviewNoteSaveTools)
                .build();
    }
}
