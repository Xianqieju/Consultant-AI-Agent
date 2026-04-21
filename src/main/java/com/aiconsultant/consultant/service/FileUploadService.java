package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.FileMetadata;
import com.aiconsultant.consultant.pojo.Result;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileUploadService extends IService<FileMetadata> {

    /**
     * 上传前校验：MD5 是否已存在（秒传）、断点分片列表、以及 RAG 是否已对该内容完成入库。
     */
    Result checkUpload(String md5, String fileName, Integer totalChunks);

    Result uploadChunk(MultipartFile file, String md5, Integer index) throws IOException;

    Result mergeChunks(String md5, String fileName) throws IOException;
}
