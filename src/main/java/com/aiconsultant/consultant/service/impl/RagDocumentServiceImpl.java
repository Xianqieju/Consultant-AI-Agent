package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.RagDocument;
import com.aiconsultant.consultant.mapper.RagDocumentMapper;
import com.aiconsultant.consultant.service.RagDocumentService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RagDocumentServiceImpl extends ServiceImpl<RagDocumentMapper, RagDocument> implements RagDocumentService {

    @Override
    public Optional<RagDocument> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        RagDocument one = getOne(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getContentHash, contentHash)
                .isNull(RagDocument::getDeletedAt)
                .last("LIMIT 1"));
        return Optional.ofNullable(one);
    }
}
