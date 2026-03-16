//package com.itheima.consultant.config;
//
//import com.itheima.consultant.aiservice.ConsultantService;
//import com.itheima.consultant.tools.UserInsightTools;
//import dev.langchain4j.memory.chat.ChatMemoryProvider;
//import dev.langchain4j.model.chat.StreamingChatLanguageModel;
//import dev.langchain4j.service.AiServices;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class AiServiceConfig {
//
//    @Bean
//    public ConsultantService consultantService(
//            StreamingChatLanguageModel streamingChatModel,
//            ChatMemoryProvider chatMemoryProvider,
//            // 注入你刚才写的工具箱
//            UserInsightTools userInsightTools) {
//
//        return AiServices.builder(ConsultantService.class)
//                .chatLanguageModel(streamingChatModel)
//                .chatMemoryProvider(chatMemoryProvider)
//                // 【核心一步】：将工具注册进 Agent
//                .tools(userInsightTools)
//                .build();
//    }
//}
