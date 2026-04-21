package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;
import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;

import java.util.List;

/**
 * 摘要向量召回 → 同章 ±n 窗口扩展 → 粗排（摘要分 + 字符 bigram Jaccard）→ 精排（query-chunk 向量余弦）。
 */
public interface RagRetrievalService {

    /**
     * @param query         用户查询
     * @param requestUserId 当前用户；仅返回 {@code rag_owner_user_id} 为该用户或全局（-1）的文档摘要
     */
    List<RagRetrievalHitDTO> retrieve(String query, Long requestUserId);

    /**
     * 在 ACL 基础上按文档 {@code expert_scope} 过滤；{@code expertScope} 为 {@link ExpertDomain#GENERAL} 时不按文档领域过滤。
     *
     * @param expertScope 当前专家领域
     */
    List<RagRetrievalHitDTO> retrieve(String query, Long requestUserId, ExpertDomain expertScope);
}
