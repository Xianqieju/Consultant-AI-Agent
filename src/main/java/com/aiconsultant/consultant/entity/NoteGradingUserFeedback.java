package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_grading_user_feedback")
public class NoteGradingUserFeedback {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long sessionId;

    private String pipelineId;

    private String gradedExcerpt;

    private String userComment;

    private String agentAnalysis;

    private LocalDateTime createTime;
}
