package com.aiconsultant.consultant.notegrading.protocol;

import lombok.Data;

@Data
public class NoteSectionTask {
    private String sectionId;
    private ExpertDomain domain;
    private String sourceText;
    private String targetGoal;
    private int scoreHint;
    private String reason;
    private String rewriteHint;
}
