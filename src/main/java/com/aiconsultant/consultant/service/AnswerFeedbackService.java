package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.pojo.AnswerFeedbackDTO;
import com.aiconsultant.consultant.pojo.Result;

public interface AnswerFeedbackService {

    /**
     * 用户对AI消息点赞/点踩（幂等覆盖同一 userId+messageId）。
     */
    Result submit(Long userId, Long sessionId, AnswerFeedbackDTO dto);

    /**
     * 从用户最近反馈中提炼“用户级短语画像补丁JSON”（tone/depth/topics/avoid/ext）。
     * 无有效反馈时返回 null。
     */
    String buildPhrasePatchJson(Long userId, Long sessionId, int limit);
}
