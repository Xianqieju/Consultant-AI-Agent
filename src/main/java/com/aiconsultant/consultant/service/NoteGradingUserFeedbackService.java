package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.pojo.NoteGradingUserFeedbackRequestDTO;
import com.aiconsultant.consultant.pojo.Result;

public interface NoteGradingUserFeedbackService {

    Result submit(Long userId, NoteGradingUserFeedbackRequestDTO dto);
}
