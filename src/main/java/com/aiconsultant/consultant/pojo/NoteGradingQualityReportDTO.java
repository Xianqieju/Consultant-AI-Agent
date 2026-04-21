package com.aiconsultant.consultant.pojo;

import lombok.Data;

/**
 * 质量检验员结构化输出：评估优先级为 幻觉、错误拼接、完整性（前两项优先）。
 */
@Data
public class NoteGradingQualityReportDTO {
    /** 0-10，越高表示幻觉风险越大 */
    private int hallucinationRiskScore;
    private String hallucinationNotes;
    /** 0-10，越高表示跨条文错误拼接问题越严重 */
    private int spliceIssueScore;
    private String spliceNotes;
    /** 0-10，越高表示越完整（与风险独立记录） */
    private int completenessScore;
    private String completenessNotes;
    /** 模型建议是否可交付 */
    private boolean passSuggested;
    private String rawJson;
}
