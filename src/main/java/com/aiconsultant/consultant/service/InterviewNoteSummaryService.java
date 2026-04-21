package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.InterviewNoteSummary;

public interface InterviewNoteSummaryService extends IService<InterviewNoteSummary> {

    boolean saveFromAgent(Long userId, Long sessionId, String title, String content);
}
