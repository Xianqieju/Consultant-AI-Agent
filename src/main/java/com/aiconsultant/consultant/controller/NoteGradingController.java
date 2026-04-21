package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.pojo.NoteGradingRequestDTO;
import com.aiconsultant.consultant.pojo.NoteGradingStructuredRequestDTO;
import com.aiconsultant.consultant.pojo.NoteGradingStructuredResponseDTO;
import com.aiconsultant.consultant.pojo.NoteGradingUserFeedbackRequestDTO;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.NoteGradingOrchestrationService;
import com.aiconsultant.consultant.service.NoteGradingUserFeedbackService;
import com.aiconsultant.consultant.utils.UserHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 多智能体笔记批改 HTTP 入口；仅返回 Aggregator 终稿。
 * 若需写入会话记忆，请在业务层仅持久化该响应正文。
 */
@RestController
@RequestMapping("/api/note-grading")
public class NoteGradingController {

    private final NoteGradingOrchestrationService orchestrationService;
    private final NoteGradingUserFeedbackService userFeedbackService;

    public NoteGradingController(
            NoteGradingOrchestrationService orchestrationService,
            NoteGradingUserFeedbackService userFeedbackService
    ) {
        this.orchestrationService = orchestrationService;
        this.userFeedbackService = userFeedbackService;
    }

    @PostMapping(value = "/grade", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Mono<String> grade(@RequestBody NoteGradingRequestDTO body) {
        String note = body == null ? null : body.getNote();
        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        return orchestrationService.gradeNoteReactive(null, userId, note);
    }

    @PostMapping(value = "/grade-structured", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NoteGradingStructuredResponseDTO> gradeStructured(@RequestBody NoteGradingStructuredRequestDTO body) {
        String note = body == null ? null : body.getNote();
        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        return orchestrationService.gradeNoteStructuredReactive(null, userId, note);
    }

    /**
     * 用户对笔记批改体验提交文本反馈；调用反馈智能体分析后落库。
     */
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result submitFeedback(@RequestBody NoteGradingUserFeedbackRequestDTO body) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("未登录");
        }
        return userFeedbackService.submit(user.getId(), body);
    }
}
