package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.FileMetadata;
import com.aiconsultant.consultant.pojo.Result;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileUploadService extends IService<FileMetadata> {
    public Result uploadChunk(MultipartFile file,String md5, Integer index) throws IOException; //分片上传

    public Result mergeChunks(String md5, String fileName) throws IOException;//分片合并
}
