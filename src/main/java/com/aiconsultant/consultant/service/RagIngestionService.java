package com.aiconsultant.consultant.service;

/**
 * 上传合并完成后：按 content_hash(MD5) 幂等触发 RAG 摘要提纲入库（异步）。
 */
public interface RagIngestionService {

    /**
     * 若该 MD5 尚未成功入库则创建任务并异步执行；已存在且解析成功则直接返回。
     */
    void scheduleIngestIfNeeded(String md5, Long userId, String absoluteFilePath, String fileName, String fileSuffix);
}
