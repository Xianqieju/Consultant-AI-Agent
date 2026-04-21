package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;
import com.aiconsultant.consultant.pojo.RagIdentityEvaluation;
import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;

import java.util.List;
import java.util.Set;

/**
 * RAG 检索：身份门禁（召回前批量判定 + 可选精排后复核）。
 */
public interface RagIdentityAccessService {

    /**
     * 对候选父文档 ID 做身份策略判定，供向量召回首轮过滤。
     * 未启用身份门禁或 GENERAL 专家时返回 {@link RagIdentityEvaluation#unrestricted()}。
     */
    RagIdentityEvaluation evaluateForRetrieval(
            Set<Long> candidateDocumentIds,
            Long requestUserId,
            ExpertDomain expertScope
    );

    /**
     * 在 ACL / expert_scope 过滤之后调用；未启用或 GENERAL 专家时原样返回。
     * 当 {@code app.rag.identity-skip-post-filter} 为 true 且身份门禁已启用时，直接返回 hits（召回环已过滤）。
     */
    List<RagRetrievalHitDTO> filterHitsByIdentity(
            List<RagRetrievalHitDTO> hits,
            Long requestUserId,
            ExpertDomain expertScope
    );
}
