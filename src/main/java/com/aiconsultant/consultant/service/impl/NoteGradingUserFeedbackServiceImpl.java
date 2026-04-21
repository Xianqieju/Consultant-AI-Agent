package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.aiservice.notegrading.NoteGradingFeedbackAgent;
import com.aiconsultant.consultant.config.NoteGradingFeedbackProperties;
import com.aiconsultant.consultant.entity.NoteGradingUserFeedback;
import com.aiconsultant.consultant.entity.UserSession;
import com.aiconsultant.consultant.mapper.NoteGradingUserFeedbackMapper;
import com.aiconsultant.consultant.pojo.NoteGradingUserFeedbackRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.NoteGradingUserFeedbackService;
import com.aiconsultant.consultant.service.UserSessionService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteGradingUserFeedbackServiceImpl implements NoteGradingUserFeedbackService {

    private final NoteGradingUserFeedbackMapper mapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final NoteGradingFeedbackAgent feedbackAgent;
    private final NoteGradingFeedbackProperties feedbackProperties;
    private final UserSessionService userSessionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result submit(Long userId, NoteGradingUserFeedbackRequestDTO dto) {
        if (userId == null) {
            return Result.fail("缺少用户");
        }
        if (dto == null || dto.getUserComment() == null || dto.getUserComment().isBlank()) {
            return Result.fail("userComment 不能为空");
        }
        int maxC = feedbackProperties.getMaxUserCommentLength();
        int maxG = feedbackProperties.getMaxGradedExcerptLength();
        String comment = truncate(dto.getUserComment(), maxC);
        String excerpt = dto.getGradedExcerpt() == null ? null : truncate(dto.getGradedExcerpt(), maxG);
        String pipelineId = truncate(dto.getPipelineId(), 64);

        Long sessionId = dto.getSessionId();
        if (sessionId != null) {
            UserSession us = userSessionService.getById(sessionId);
            if (us == null || us.getUserId() == null || !us.getUserId().equals(userId)) {
                return Result.fail("会话不存在或无权限");
            }
        }

        String analysis = buildAgentAnalysis(comment, excerpt == null ? "" : excerpt);

        NoteGradingUserFeedback row = new NoteGradingUserFeedback();
        row.setId(snowflakeIdWorker.nextId());
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setPipelineId(pipelineId);
        row.setGradedExcerpt(excerpt);
        row.setUserComment(comment);
        row.setAgentAnalysis(analysis);
        row.setCreateTime(LocalDateTime.now());
        mapper.insert(row);
        return Result.ok(row.getId());
    }

    private String buildAgentAnalysis(String userComment, String gradedExcerpt) {
        if (!feedbackProperties.isEnabled()) {
            return disabledAnalysisJson();
        }
        try {
            String raw = feedbackAgent.analyzeFeedback(userComment, gradedExcerpt);
            if (raw == null || raw.isBlank()) {
                return fallbackAnalysisJson("empty_agent_response");
            }
            return truncate(raw, 8000);
        } catch (Exception e) {
            log.warn("NoteGradingFeedbackAgent failed: {}", e.getMessage());
            return fallbackAnalysisJson(e.getClass().getSimpleName() + ":" + truncate(e.getMessage(), 400));
        }
    }

    private String disabledAnalysisJson() {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("sentiment", "neutral");
            n.put("summary", "反馈智能体已关闭，仅保存用户原文。");
            n.putArray("themes");
            n.putArray("actionHints");
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"sentiment\":\"neutral\",\"summary\":\"反馈智能体已关闭\",\"themes\":[],\"actionHints\":[]}";
        }
    }

    private String fallbackAnalysisJson(String reason) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("sentiment", "neutral");
            n.put("summary", "智能体分析失败，已降级保存。");
            n.put("errorHint", reason);
            n.putArray("themes");
            n.putArray("actionHints");
            return objectMapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"sentiment\":\"neutral\",\"summary\":\"智能体分析失败\",\"themes\":[],\"actionHints\":[]}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
