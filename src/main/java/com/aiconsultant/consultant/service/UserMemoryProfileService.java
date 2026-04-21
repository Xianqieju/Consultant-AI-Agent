package com.aiconsultant.consultant.service;

public interface UserMemoryProfileService {

    /**
     * 返回用户级短语画像（为空壳时返回 null，用于 prompt 注入时减少干扰）。
     */
    String getPhrasesForMemory(Long userId);

    /**
     * 将会话画像中的短语合并进用户级短语画像（去重 + 上限裁剪）。
     */
    void mergeFromSessionProfile(Long userId, String sessionProfileJson);
}
