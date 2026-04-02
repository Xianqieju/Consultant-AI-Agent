package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.pojo.Result;
import reactor.core.publisher.Flux;

public interface ChatMessageService extends IService<ChatMessage> {
    /**
     * 处理流式对话的核心编排逻辑
     */
    Flux<String> streamChatProcess(String message, Long agentId, ChatAgentDTO agent, UserDTO userDTO);

    Result getHistoryAndRefreshMemory();
}
