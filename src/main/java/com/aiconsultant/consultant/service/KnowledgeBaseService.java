package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.KnowledgeBase;
import org.springframework.core.io.Resource;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    void initGlobalKnowledge();

    void processAndIngest(Resource resource,String fileName, String hash, Long userId, KnowledgeBase oldRecord);
}
