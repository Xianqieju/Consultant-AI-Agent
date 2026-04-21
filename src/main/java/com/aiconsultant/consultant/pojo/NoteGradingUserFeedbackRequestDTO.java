package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class NoteGradingUserFeedbackRequestDTO {
    /** 用户反馈正文（必填） */
    private String userComment;
    /** 可选：批改 pipelineId */
    private String pipelineId;
    /** 可选：会话 ID（若传则校验归属） */
    private Long sessionId;
    /** 可选：用户摘录的批改输出片段 */
    private String gradedExcerpt;
}
