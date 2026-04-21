package com.aiconsultant.consultant.notegrading.access;

import com.aiconsultant.consultant.mapper.UserExpertPermissionMapper;
import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;

/**
 * 基于 {@code user_expert_permission} 的校验：未登录用户对非 GENERAL 专家一律拒绝。
 */
public class DatabaseExpertAccessPolicy implements ExpertAccessPolicy {

    private final UserExpertPermissionMapper mapper;

    public DatabaseExpertAccessPolicy(UserExpertPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean canAccess(Long userId, ExpertDomain expert) {
        if (expert == null || expert == ExpertDomain.GENERAL) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        return mapper.countAllowed(userId, expert.name()) > 0;
    }
}
