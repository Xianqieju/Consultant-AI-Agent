package com.aiconsultant.consultant.notegrading.protocol;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NoteTaskPlan {
    private NoteTaskEnvelope envelope;
    private String routingReason;
    private String globalConstraints;
    private int overallScore;
    private String overallScoreReason;
    private List<NoteSectionTask> tasks = new ArrayList<>();
}
