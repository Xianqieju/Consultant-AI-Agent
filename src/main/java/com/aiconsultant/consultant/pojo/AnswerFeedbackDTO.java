package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class AnswerFeedbackDTO {
    private Long messageId;
    /** 1=点赞,-1=点踩 */
    private Integer attitude;
    private String reasonTag;
    private String reasonText;
}
