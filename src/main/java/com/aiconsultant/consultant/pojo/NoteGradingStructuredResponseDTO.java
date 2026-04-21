package com.aiconsultant.consultant.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NoteGradingStructuredResponseDTO {
    private String pipelineId;
    private String finalText;
    private int overallScore;
    private String overallScoreReason;
    private NoteGradingQualityReportDTO qualityReport;
    /** 排版总结员是否已处理终稿 */
    private boolean formattedBySummarizer;
    private List<SectionScoreDTO> sections = new ArrayList<>();

    @Data
    public static class SectionScoreDTO {
        private String sectionId;
        private String domain;
        private int scoreHint;
        private String reason;
        private String rewriteHint;
        private List<RagCitationDTO> ragCitations = new ArrayList<>();
    }
}
