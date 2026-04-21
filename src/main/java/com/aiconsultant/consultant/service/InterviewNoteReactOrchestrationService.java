package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.pojo.InterviewReactResultDTO;

/**
 * LangGraph 风格简化编排：入口审查 → 老师产出 → 质量打分 → 不达标则带反馈回老师改写，直至达标或达最大轮次。
 */
public interface InterviewNoteReactOrchestrationService {

    InterviewReactResultDTO runReactLoop(String userNote);
}
