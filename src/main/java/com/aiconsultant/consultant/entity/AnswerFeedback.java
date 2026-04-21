package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("answer_feedback")
public class AnswerFeedback {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long messageId;

    /** 1=点赞,-1=点踩 */
    private Integer attitude;

    private String reasonTag;

    private String reasonText;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
