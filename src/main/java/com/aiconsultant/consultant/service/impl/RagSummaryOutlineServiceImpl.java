package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.RagSummaryOutline;
import com.aiconsultant.consultant.mapper.RagSummaryOutlineMapper;
import com.aiconsultant.consultant.service.RagSummaryOutlineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagSummaryOutlineServiceImpl extends ServiceImpl<RagSummaryOutlineMapper, RagSummaryOutline>
        implements RagSummaryOutlineService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateSummary(Long summaryId) {
        RagSummaryOutline row = getById(summaryId);
        if (row == null) {
            throw new IllegalArgumentException("摘要不存在: " + summaryId);
        }
        if (row.getDeletedAt() != null) {
            throw new IllegalStateException("已逻辑删除的摘要不可激活: " + summaryId);
        }
        Long chunkId = row.getChunkId();
        lambdaUpdate()
                .eq(RagSummaryOutline::getChunkId, chunkId)
                .isNull(RagSummaryOutline::getDeletedAt)
                .set(RagSummaryOutline::getIsAvailable, 0)
                .update();
        lambdaUpdate()
                .eq(RagSummaryOutline::getId, summaryId)
                .set(RagSummaryOutline::getIsAvailable, 1)
                .update();

        long activeCount = lambdaQuery()
                .eq(RagSummaryOutline::getChunkId, chunkId)
                .eq(RagSummaryOutline::getIsAvailable, 1)
                .isNull(RagSummaryOutline::getDeletedAt)
                .count();
        if (activeCount != 1) {
            throw new IllegalStateException("同一 chunk 可用摘要数量异常，期望 1 实际 " + activeCount + ", chunkId=" + chunkId);
        }
    }
}
