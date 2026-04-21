package com.aiconsultant.consultant.pojo;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试笔记 ReAct 编排结果（审查员门禁 + 老师多轮 + 质量打分）。
 */
@Data
@Builder
public class InterviewReactResultDTO {

    /** 是否通过入口审查（ACCEPT） */
    private boolean gateAccepted;
    /** 入口审查原始输出 */
    private String gateRaw;

    private String finalDraft;
    private int roundsUsed;
    private int lastScore;
    private boolean scoreSatisfied;

    @Builder.Default
    private List<String> trace = new ArrayList<>();
}
