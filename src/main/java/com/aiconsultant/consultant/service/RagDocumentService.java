package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.RagDocument;

import java.util.Optional;

public interface RagDocumentService extends IService<RagDocument> {

    Optional<RagDocument> findByContentHash(String contentHash);
}
