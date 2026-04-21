package com.aiconsultant.consultant.service.impl;

import com.aiconsultant.consultant.entity.RagDocument;
import com.aiconsultant.consultant.service.RagDocumentService;
import com.aiconsultant.consultant.service.RagIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class RagIngestionServiceImpl implements RagIngestionService {

    @Autowired
    private RagDocumentService ragDocumentService;
    @Autowired
    private RagIngestionWorker ragIngestionWorker;

    @Override
    public void scheduleIngestIfNeeded(String md5, Long userId, String absoluteFilePath, String fileName, String fileSuffix) {
        if (md5 == null || md5.isBlank()) {
            return;
        }
        synchronized (md5.intern()) {
            Optional<RagDocument> opt = ragDocumentService.findByContentHash(md5);
            if (opt.isPresent() && Objects.equals(1, opt.get().getParseStatus())) {
                log.info("RAG 已入库完成，跳过: md5={}", md5);
                return;
            }
            if (opt.isPresent() && Objects.equals(0, opt.get().getParseStatus())) {
                log.info("RAG 正在处理中，跳过重复调度: md5={}", md5);
                return;
            }

            RagDocument doc;
            if (opt.isEmpty()) {
                doc = new RagDocument()
                        .setUserId(userId)
                        .setTitle(fileName != null ? fileName : "")
                        .setSourceType(fileSuffix != null ? fileSuffix.toLowerCase() : "unknown")
                        .setStorageUri(absoluteFilePath != null ? absoluteFilePath : "")
                        .setContentHash(md5)
                        .setLanguage("zh")
                        .setEduRelevance(1)
                        .setParseStatus(0)
                        .setVersion(1)
                        .setIsAvailable(1)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now());
                ragDocumentService.save(doc);
            } else {
                doc = opt.get();
                if (Objects.equals(2, doc.getParseStatus())) {
                    doc.setParseStatus(0);
                    doc.setUpdatedAt(LocalDateTime.now());
                    ragDocumentService.updateById(doc);
                } else {
                    return;
                }
            }
            ragIngestionWorker.runIngestAsync(doc.getId(), absoluteFilePath, fileName, fileSuffix);
        }
    }
}
