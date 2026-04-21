package com.aiconsultant.consultant.service;

public interface MemorySummaryTriggerService {

    /**
     * 在 AI 回复流式完成后调用：满足「用户消息数 % N == 0」且 Redis 窗口已满时，写入发件箱并投递 MQ。
     * 失败仅打日志，不影响对话主链路。
     */
    void tryEnqueueAfterAssistantReply(Long sessionId, Long userId);
}
