package com.aiconsultant.consultant.aiservice;

import com.aiconsultant.consultant.enums.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * 意图识别路由器
 * 注意：这里建议配置一个速度最快、最便宜的模型（比如 GPT-4o-mini 或 GLM-4-Flash）
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel" // 这里用非流式的普通模型即可，因为只是做瞬间的分类
)
public interface IntentRouterService {

    @SystemMessage("""
            你是一个精准的意图识别分类器。
            请分析用户的输入，判断其核心意图，并严格返回指定的枚举名称：
            1. 如果用户明确要求“记录”、“保存”、“总结笔记”，或者提到把当前对话存下来，请返回 NOTE_TAKING。
            2. 如果用户明确要求“批改/纠错/润色/改写/补充/修改”学习笔记，或提供了一段要被批改的笔记内容，请返回 NOTE_EDIT_GRADE。
            3. 其他所有情况，包括日常打招呼、倾诉烦恼、询问建议，请一律返回 CASUAL_CHAT。
            
            你只能输出枚举名称，不要包含任何解释、标点符号或其他额外字符。
            """)
    UserIntent classifyIntent(@UserMessage String userMessage);
}
