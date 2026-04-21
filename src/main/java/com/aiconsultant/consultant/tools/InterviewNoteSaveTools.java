package com.aiconsultant.consultant.tools;

import com.aiconsultant.consultant.service.InterviewNoteSummaryService;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 面试笔记总结员：将最终总结写入数据库（需与 HTTP 请求线程配合 {@link SessionHolder}）。
 */
@Slf4j
@Component("interviewNoteSaveTools")
public class InterviewNoteSaveTools implements BaseAgentTool {

    @Autowired
    private InterviewNoteSummaryService interviewNoteSummaryService;

    @Tool("在完成面试笔记的结构化总结后调用，将总结持久化到用户档案。禁止在未形成完整总结前调用。")
    public String saveInterviewNoteSummary(
            @P("总结标题，不超过20字") String title,
            @P("总结正文，Markdown") String content
    ) {
        if (UserHolder.getUser() == null) {
            return "系统提示：未登录，无法保存。";
        }
        Long userId = UserHolder.getUser().getId();
        Long sessionId = SessionHolder.getSessionId();
        log.info("保存面试笔记总结 userId={}, sessionId={}, title={}", userId, sessionId, title);
        boolean ok = interviewNoteSummaryService.saveFromAgent(userId, sessionId, title, content);
        return ok ? "系统提示：总结已保存。" : "系统提示：保存失败。";
    }
}
