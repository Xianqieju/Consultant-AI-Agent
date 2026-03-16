package com.aiconsultant.consultant.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import java.util.List;

@AiService(chatModel = "openAiChatModel")
public interface ToolRouterService {

    @SystemMessage("""
            你是一个精准的任务分发器。请分析用户输入，并从以下工具池中选择合适的工具名称。
            
            【工具池可用标识符】：
            1. weatherTools : 涉及询问天气、气温，或需要为日记增加环境描写时。
            2. userInsightTools : 涉及“记录”、“保存”、“总结笔记”等指令时。
            3. knowledgeBaseTools : 涉及查询高考政策、分数线、专业介绍、心理学客观知识时。
            
            【输出规范】：
            请严格分析语境。只需要输出需要调用的“工具标识符”列表。
            如果需要多个工具（例如记录日记且带天气），请输出多个。
            如果是纯日常闲聊、情绪倾诉，不需要任何工具，请输出空列表 []。
            切勿输出任何其他解释性文本。
            """)
    List<String> determineRequiredTools(@UserMessage String userMessage);
}
