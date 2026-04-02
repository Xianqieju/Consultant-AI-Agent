package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.ChatAgent;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.Result;
import reactor.core.publisher.Mono;

public interface ChatAgentService extends IService<ChatAgent> {

    // 返回 Mono，将调度权上交给框架
    Mono<ChatAgentDTO> getAgentById(Long id);

    // 获取所有智能体的精简描述列表，同样返回 Mono
    Mono<Result> getAgentDescriptionList();
}