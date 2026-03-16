package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.UserNote;

public interface UserNoteService extends IService<UserNote> {

    /**
     * 供 Agent Tool 调用的笔记保存方法
     */
    boolean saveNoteFromAgent(Long userId, String title, String content, String tag);
}
