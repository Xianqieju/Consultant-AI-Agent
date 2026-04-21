package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传：与 {@link FileUploadServiceImpl} 配套；合并成功后异步触发 RAG 摘要提纲入库（同 MD5 幂等）。
 */
@RestController
@RequestMapping("/file")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/check-upload")
    public Result checkUpload(
            @RequestParam("md5") String md5,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "totalChunks", required = false) Integer totalChunks
    ) {
        return fileUploadService.checkUpload(md5, fileName, totalChunks);
    }

    @PostMapping("/chunk")
    public Result uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("md5") String md5,
            @RequestParam("index") Integer index
    ) throws Exception {
        return fileUploadService.uploadChunk(file, md5, index);
    }

    @PostMapping("/merge")
    public Result mergeChunks(
            @RequestParam("md5") String md5,
            @RequestParam("fileName") String fileName
    ) throws Exception {
        return fileUploadService.mergeChunks(md5, fileName);
    }
}
