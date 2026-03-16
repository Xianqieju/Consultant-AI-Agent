package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.UserSession;
import com.aiconsultant.consultant.pojo.UserSessionDTO;

import java.util.List;

public interface UserSessionService extends IService<UserSession> {
    List<UserSessionDTO> getUserSessionList(Long userId);

    Long createSession(Long userId, Long agentId, String firstMessage);
}
