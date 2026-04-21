package com.aiconsultant.consultant.pojo;

import lombok.Builder;
import lombok.Data;

/**
 * RAG 检索最终结果（精排后）：摘要命中 → 窗口扩展 chunk → 粗排 → 精排。
 */
@Data
@Builder
public class RagRetrievalHitDTO {

    private Long documentId;
    private Long chapterId;
    private Long chunkId;
    private Long summaryId;

    private String outlineSnippet;
    private String chunkTextSnippet;

    /** 摘要向量召回分（来自向量库） */
    private double summaryEmbeddingScore;
    /** 粗排融合分 */
    private double coarseScore;
    /** 精排：query-chunk 向量余弦相似度 */
    private double fineScore;
}
