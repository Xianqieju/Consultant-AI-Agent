package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.UserSession;
import com.aiconsultant.consultant.mapper.UserSessionMapper;
import com.aiconsultant.consultant.pojo.UserSessionDTO;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
@Service
@Slf4j
public class UserSessionServiceImpl extends ServiceImpl<UserSessionMapper,UserSession> implements UserSessionService {

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

    @Override
    public List<UserSessionDTO> getUserSessionList(Long userId) {
        // 1. 查询数据库：过滤 userId，过滤逻辑删除，按更新时间倒序
        List<UserSession> sessions = lambdaQuery()
                .eq(UserSession::getUserId, userId)
                .orderByDesc(UserSession::getUpdateTime) // 最近更新的在前
                .list();

        // 2. 将 Entity 转化为 DTO 返回给前端
        return sessions.stream().map(entity -> {
            UserSessionDTO dto = new UserSessionDTO();
            BeanUtils.copyProperties(entity, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(Long userId, Long agentId, String firstMessage) {
        // 1. 使用雪花算法生成唯一的 sessionId
        Long sessionId = snowflakeIdWorker.nextId();
        LocalDateTime time = LocalDateTime.now();
        // 2. 构造会话实体
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setAgentId(agentId);
        session.setCreateTime(time);
        session.setUpdateTime(time);
        // 3. 生成标题：取首条消息的前 15 个字，若为空则设为“新对话”
        String title = "新对话";
        if (StrUtil.isNotBlank(firstMessage)) {
            title = firstMessage.length() > 15 ? firstMessage.substring(0, 15) + "..." : firstMessage;
        }
        session.setTitle(title);

        // 4. 入库保存
        boolean saved = this.save(session);

        if (!saved) {
            throw new RuntimeException("创建会话失败，数据库写入异常");
        }

        log.info("用户 {} 成功创建新会话: {}, 标题: {}", userId, sessionId, title);

        // 5. 返回生成的 sessionId
        Long HSession = SessionHolder.getSessionId();
        if(HSession == null){
            SessionHolder.saveSessionId(sessionId);
        }
        return sessionId;
    }
}
