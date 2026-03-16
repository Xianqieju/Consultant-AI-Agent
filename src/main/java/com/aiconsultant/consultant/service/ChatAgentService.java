package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.ChatAgent;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.Result;

public interface ChatAgentService extends IService<ChatAgent> {
    ChatAgentDTO getAgentById(Long id);

        // 获取所有智能体的精简描述列表
    Result getAgentDescriptionList();
}
