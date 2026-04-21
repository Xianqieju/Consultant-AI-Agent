package com.aiconsultant.consultant.service;

public interface ChatSessionMemoryProfileService {

    /**
     * 供前端/管理：返回当前会话画像 JSON（无记录则返回空壳 JSON）。
     */
    String getProfileJson(Long userId, Long sessionId);

    /**
     * 供模型上下文拼装：无画像或仍为空壳时返回 null，不注入额外 System 块。
     */
    String getProfileJsonForMemory(Long sessionId);

    /**
     * 全量覆盖写入（需 JSON 对象）；事务提交后失效 Redis 双键。
     */
    void saveProfile(Long userId, Long sessionId, String profileJson);

    /**
     * 通过会话查询归属用户（不存在返回 null）。
     */
    Long findUserIdBySession(Long sessionId);
}
