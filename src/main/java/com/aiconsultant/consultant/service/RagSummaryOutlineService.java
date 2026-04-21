package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.RagSummaryOutline;

/**
 * 摘要提纲：同一 {@link RagSummaryOutline#getChunkId()} 下仅允许一条 {@code is_available=1}（未逻辑删除）。
 * MySQL 5.7 场景由 {@link #activateSummary(Long)} 在事务内切换并校验条数。
 */
public interface RagSummaryOutlineService extends IService<RagSummaryOutline> {

    /**
     * 将该摘要设为当前可用：同 chunk 下其余摘要全部置为不可用，再置本行为可用。
     */
    void activateSummary(Long summaryId);
}
