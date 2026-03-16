package com.aiconsultant.consultant.runner;

import com.aiconsultant.consultant.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KnowledgeBaseRunner implements CommandLineRunner {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public void run(String... args) throws Exception {
        log.info("======== 开始全局知识库自检 ========");
        // 调用你之前写好的 Service 逻辑
        knowledgeBaseService.initGlobalKnowledge();
        log.info("======== 知识库自检完成 ========");
    }
}