package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.aiservice.interview.InterviewNoteQualityGateAgent;
import com.aiconsultant.consultant.aiservice.interview.InterviewNoteReviewerAgent;
import com.aiconsultant.consultant.aiservice.interview.InterviewNoteTeacherAgent;
import com.aiconsultant.consultant.pojo.InterviewReactResultDTO;
import com.aiconsultant.consultant.service.InterviewNoteReactOrchestrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 模仿 LangGraph 的「状态机循环」：节点 = 老师 / 审查员；边 = 分数与轮次。
 * 不引入 LangGraph 依赖，便于在 Spring 内观测与单测。
 */
@Slf4j
@Service
public class InterviewNoteReactOrchestrationServiceImpl implements InterviewNoteReactOrchestrationService {

    @Value("${app.interview.react.max-rounds:3}")
    private int maxRounds;
    @Value("${app.interview.react.min-score:7}")
    private int minScore;

    @Autowired
    private InterviewNoteReviewerAgent gateReviewer;
    @Autowired
    private InterviewNoteTeacherAgent teacher;
    @Autowired
    private InterviewNoteQualityGateAgent qualityGate;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public InterviewReactResultDTO runReactLoop(String userNote) {
        List<String> trace = new ArrayList<>();

        if (userNote == null || userNote.isBlank()) {
            return InterviewReactResultDTO.builder()
                    .gateAccepted(false)
                    .gateRaw("")
                    .finalDraft("")
                    .roundsUsed(0)
                    .lastScore(0)
                    .scoreSatisfied(false)
                    .trace(trace)
                    .build();
        }

        String gateOut = gateReviewer.review(userNote);
        trace.add("gate:" + gateOut);
        boolean accept = gateOut != null && gateOut.trim().toUpperCase().contains("ACCEPT");
        if (!accept) {
            return InterviewReactResultDTO.builder()
                    .gateAccepted(false)
                    .gateRaw(gateOut != null ? gateOut : "")
                    .finalDraft("")
                    .roundsUsed(0)
                    .lastScore(0)
                    .scoreSatisfied(false)
                    .trace(trace)
                    .build();
        }

        String draft = teacher.teach(userNote);
        trace.add("teach_initial");
        int lastScore = 0;

        for (int round = 0; round < maxRounds; round++) {
            String rawJson = qualityGate.scoreDraft(userNote, draft);
            trace.add("quality_raw_r" + round + ":" + truncate(rawJson, 200));
            lastScore = parseScore(rawJson);
            String feedback = parseFeedback(rawJson);
            trace.add("quality_score_r" + round + ":" + lastScore);

            if (lastScore >= minScore) {
                return InterviewReactResultDTO.builder()
                        .gateAccepted(true)
                        .gateRaw(gateOut)
                        .finalDraft(draft)
                        .roundsUsed(round + 1)
                        .lastScore(lastScore)
                        .scoreSatisfied(true)
                        .trace(trace)
                        .build();
            }
            if (round == maxRounds - 1) {
                break;
            }
            draft = teacher.rewriteWithFeedback(userNote, draft, feedback);
            trace.add("rewrite_r" + (round + 1));
        }

        return InterviewReactResultDTO.builder()
                .gateAccepted(true)
                .gateRaw(gateOut)
                .finalDraft(draft)
                .roundsUsed(maxRounds)
                .lastScore(lastScore)
                .scoreSatisfied(lastScore >= minScore)
                .trace(trace)
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private int parseScore(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String cleaned = stripJsonFence(raw.trim());
        try {
            JsonNode n = objectMapper.readTree(cleaned);
            if (n.has("score") && n.get("score").isNumber()) {
                return Math.max(0, Math.min(10, n.get("score").asInt()));
            }
        } catch (Exception e) {
            log.warn("质量 JSON 解析失败: {}", e.getMessage());
        }
        return 0;
    }

    private String parseFeedback(String raw) {
        if (raw == null || raw.isBlank()) {
            return "请整体补充细节与可落地要点。";
        }
        String cleaned = stripJsonFence(raw.trim());
        try {
            JsonNode n = objectMapper.readTree(cleaned);
            if (n.has("feedback")) {
                return n.get("feedback").asText("请整体补充细节与可落地要点。");
            }
        } catch (Exception e) {
            log.warn("质量 feedback 解析失败: {}", e.getMessage());
        }
        return "请整体补充细节与可落地要点。";
    }

    private static String stripJsonFence(String s) {
        if (s.startsWith("```")) {
            int i = s.indexOf('{');
            int j = s.lastIndexOf('}');
            if (i >= 0 && j > i) {
                return s.substring(i, j + 1);
            }
        }
        return s;
    }
}
