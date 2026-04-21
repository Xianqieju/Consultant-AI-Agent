package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.AnswerFeedback;
import com.aiconsultant.consultant.entity.ChatMessage;
import com.aiconsultant.consultant.mapper.AnswerFeedbackMapper;
import com.aiconsultant.consultant.mapper.ChatMessageMapper;
import com.aiconsultant.consultant.pojo.AnswerFeedbackDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.AnswerFeedbackService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerFeedbackServiceImpl implements AnswerFeedbackService {

    private final AnswerFeedbackMapper answerFeedbackMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result submit(Long userId, Long sessionId, AnswerFeedbackDTO dto) {
        if (userId == null || sessionId == null) {
            return Result.fail("缺少用户或会话上下文");
        }
        if (dto == null || dto.getMessageId() == null) {
            return Result.fail("messageId 不能为空");
        }
        if (dto.getAttitude() == null || (dto.getAttitude() != 1 && dto.getAttitude() != -1)) {
            return Result.fail("attitude 必须为 1(点赞) 或 -1(点踩)");
        }

        ChatMessage msg = chatMessageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getId, dto.getMessageId())
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getSessionId, sessionId)
                .last("LIMIT 1"));
        if (msg == null) {
            return Result.fail("消息不存在或无权限");
        }
        if (msg.getRole() == null || msg.getRole() != 1) {
            return Result.fail("仅支持对 AI 回复消息进行反馈");
        }

        AnswerFeedback existed = answerFeedbackMapper.selectOne(new LambdaQueryWrapper<AnswerFeedback>()
                .eq(AnswerFeedback::getUserId, userId)
                .eq(AnswerFeedback::getMessageId, dto.getMessageId())
                .last("LIMIT 1"));
        if (existed == null) {
            AnswerFeedback row = new AnswerFeedback();
            row.setId(snowflakeIdWorker.nextId());
            row.setUserId(userId);
            row.setSessionId(sessionId);
            row.setMessageId(dto.getMessageId());
            row.setAttitude(dto.getAttitude());
            row.setReasonTag(truncate(dto.getReasonTag(), 64));
            row.setReasonText(truncate(dto.getReasonText(), 512));
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            answerFeedbackMapper.insert(row);
        } else {
            existed.setAttitude(dto.getAttitude());
            existed.setReasonTag(truncate(dto.getReasonTag(), 64));
            existed.setReasonText(truncate(dto.getReasonText(), 512));
            existed.setUpdateTime(LocalDateTime.now());
            answerFeedbackMapper.updateById(existed);
        }
        return Result.ok();
    }

    @Override
    public String buildPhrasePatchJson(Long userId, Long sessionId, int limit) {
        if (userId == null || sessionId == null || limit <= 0) {
            return null;
        }
        List<AnswerFeedback> rows = answerFeedbackMapper.selectList(new LambdaQueryWrapper<AnswerFeedback>()
                .eq(AnswerFeedback::getUserId, userId)
                .eq(AnswerFeedback::getSessionId, sessionId)
                .orderByDesc(AnswerFeedback::getUpdateTime)
                .last("LIMIT " + limit));
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        Set<String> tone = new LinkedHashSet<>();
        Set<String> depth = new LinkedHashSet<>();
        Set<String> topics = new LinkedHashSet<>();
        Set<String> avoid = new LinkedHashSet<>();

        for (AnswerFeedback row : rows) {
            String tag = normalize(row.getReasonTag());
            String text = normalize(row.getReasonText());
            boolean like = row.getAttitude() != null && row.getAttitude() == 1;
            boolean dislike = row.getAttitude() != null && row.getAttitude() == -1;
            if (!like && !dislike) {
                continue;
            }
            if (containsAny(tag, text, "too_long", "啰嗦", "太长", "冗长")) {
                if (dislike) {
                    tone.add("简洁");
                    avoid.add("长篇背景");
                } else {
                    depth.add("适度展开");
                }
            }
            if (containsAny(tag, text, "too_short", "太短", "不够细")) {
                if (dislike) {
                    depth.add("详细");
                } else {
                    tone.add("简洁");
                }
            }
            if (containsAny(tag, text, "off_topic", "跑题", "偏题")) {
                if (dislike) {
                    avoid.add("无关延展");
                }
            }
            if (containsAny(tag, text, "step", "步骤", "分步")) {
                if (like) {
                    depth.add("分步");
                } else {
                    avoid.add("过度分步");
                }
            }
            if (containsAny(tag, text, "code", "代码")) {
                topics.add("代码");
            }
            if (containsAny(tag, text, "gaokao", "志愿", "高考")) {
                topics.add("高考志愿");
            }
            if (containsAny(tag, text, "psych", "焦虑", "心理")) {
                topics.add("心理支持");
            }
        }

        if (tone.isEmpty() && depth.isEmpty() && topics.isEmpty() && avoid.isEmpty()) {
            return null;
        }
        ObjectNode patch = objectMapper.createObjectNode();
        patch.set("tone", toArray(tone));
        patch.set("depth", toArray(depth));
        patch.set("topics", toArray(topics));
        patch.set("avoid", toArray(avoid));
        patch.putObject("ext");
        return patch.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static boolean containsAny(String a, String b, String... keys) {
        for (String k : keys) {
            if ((a != null && a.contains(k)) || (b != null && b.contains(k))) {
                return true;
            }
        }
        return false;
    }

    private ArrayNode toArray(Set<String> set) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (String s : set) {
            arr.add(s);
        }
        return arr;
    }
}
