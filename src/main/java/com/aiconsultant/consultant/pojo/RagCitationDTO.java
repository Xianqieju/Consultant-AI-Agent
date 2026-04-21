package com.aiconsultant.consultant.pojo;

import lombok.Data;

@Data
public class RagCitationDTO {
    private Long documentId;
    private Long chunkId;
    private String quote;
    /** 0-1 或模型给出的相关性描述 */
    private String relevance;
}
