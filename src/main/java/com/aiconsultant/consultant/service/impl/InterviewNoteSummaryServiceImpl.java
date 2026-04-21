package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.InterviewNoteSummary;
import com.aiconsultant.consultant.mapper.InterviewNoteSummaryMapper;
import com.aiconsultant.consultant.service.InterviewNoteSummaryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InterviewNoteSummaryServiceImpl extends ServiceImpl<InterviewNoteSummaryMapper, InterviewNoteSummary>
        implements InterviewNoteSummaryService {

    @Override
    public boolean saveFromAgent(Long userId, Long sessionId, String title, String content) {
        InterviewNoteSummary row = new InterviewNoteSummary()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setTitle(title == null ? "" : title)
                .setContent(content == null ? "" : content)
                .setVersion(1)
                .setIsAvailable(1)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        return save(row);
    }
}
