package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.UserNote;
import com.aiconsultant.consultant.mapper.UserNoteMapper;
import com.aiconsultant.consultant.service.UserNoteService;
import com.aiconsultant.consultant.utils.SnowflakeIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserNoteServiceImpl extends ServiceImpl<UserNoteMapper, UserNote> implements UserNoteService {

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

    @Override
    public boolean saveNoteFromAgent(Long userId, String title, String content, String tag) {
        UserNote note = new UserNote();
        // 显式生成并设置ID，也可依赖 MyBatis-Plus 自动填充
        note.setId(snowflakeIdWorker.nextId());
        note.setUserId(userId);
        note.setTitle(title);
        note.setContent(content);
        note.setTag(tag);
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());

        return this.save(note);
    }
}