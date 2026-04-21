package com.aiconsultant.consultant.notegrading.access;

import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;

/**
 * 开发环境或未启用库表校验时放行所有专家。
 */
public class AllowAllExpertAccessPolicy implements ExpertAccessPolicy {

    @Override
    public boolean canAccess(Long userId, ExpertDomain expert) {
        return true;
    }
}
