package com.aiconsultant.consultant.notegrading.access;

import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;

/**
 * 在技术指导产出任务后、执行专家前，校验当前用户是否可调用该专家。
 */
public interface ExpertAccessPolicy {

    boolean canAccess(Long userId, ExpertDomain expert);
}
