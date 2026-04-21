package com.aiconsultant.consultant.service;

public interface MemorySummaryProcessorService {

    /**
     * 消费发件箱：领取 PENDING → 摘要合并 → 写库 → 裁剪 Redis 窗口 → DONE。
     */
    void processOutbox(Long outboxId);
}
