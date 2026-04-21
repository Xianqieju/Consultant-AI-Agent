package com.aiconsultant.consultant.pojo;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 召回阶段身份门禁的批量判定结果。
 *
 * @param gateActive      true 表示已启用身份且专家非 GENERAL，{@link #allowedDocumentIds()} 有效
 * @param allowedDocumentIds 候选父文档 ID 中允许进入粗排的子集；gateActive 为 false 时为 null（表示不限制）
 * @param identitiesByDocumentId 候选文档在库中的身份绑定（用于与向量 metadata 对账）；无绑定文档可能不在 map 中
 */
public record RagIdentityEvaluation(
        boolean gateActive,
        Set<Long> allowedDocumentIds,
        Map<Long, Set<Long>> identitiesByDocumentId
) {
    public static RagIdentityEvaluation unrestricted() {
        return new RagIdentityEvaluation(false, null, Collections.emptyMap());
    }
}
