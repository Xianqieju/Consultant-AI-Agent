package com.aiconsultant.consultant.controller;


import com.aiconsultant.consultant.pojo.UserSessionDTO;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class SessionContorller {
    @Resource
    private UserSessionService userSessionService;

    @GetMapping("/allSession")
    public List<UserSessionDTO> getAllSessions() {
        // 直接从 UserHolder 拿到当前登录用户的 ID
        Long userId = UserHolder.getUser().getId();

        // 调用业务层获取 DTO 列表
        return userSessionService.getUserSessionList(userId);
    }
}
