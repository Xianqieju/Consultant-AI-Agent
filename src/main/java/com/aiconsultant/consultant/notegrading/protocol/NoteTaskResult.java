package com.aiconsultant.consultant.notegrading.protocol;

import com.aiconsultant.consultant.pojo.RagCitationDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NoteTaskResult {
    private NoteTaskEnvelope envelope;
    private String sectionId;
    private ExpertDomain domain;
    private String rewriteText;
    private String issues;
    private String addressedHints;
    private Integer workerScore;
    private String workerScoreReason;
    private List<RagCitationDTO> ragCitations = new ArrayList<>();
}
