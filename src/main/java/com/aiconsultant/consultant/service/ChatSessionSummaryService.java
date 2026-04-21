package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.entity.ChatSessionSummary;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChatSessionSummaryService extends IService<ChatSessionSummary> {

    /**
     * 读库摘要正文（不经 Redis）；无记录返回 null。
     */
    String loadSummaryTextFromDb(Long sessionId);

    /**
     * 合并写入或更新摘要；乐观锁冲突时内部重试。
     */
    void saveMergedSummary(Long sessionId, Long userId, String mergedText);
}
