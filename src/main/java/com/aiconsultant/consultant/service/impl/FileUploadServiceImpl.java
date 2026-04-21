package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.FileMetadata;
import com.aiconsultant.consultant.mapper.FileMetadataMapper;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.FileUploadService;
import com.aiconsultant.consultant.service.RagDocumentService;
import com.aiconsultant.consultant.service.RagIngestionService;
import com.aiconsultant.consultant.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;

@Service
@Slf4j
public class FileUploadServiceImpl extends ServiceImpl<FileMetadataMapper, FileMetadata> implements FileUploadService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RagDocumentService ragDocumentService;
    @Autowired
    private RagIngestionService ragIngestionService;

    @Override
    public Result checkUpload(String md5, String fileName, Integer totalChunks) {
        // 1. 检查数据库，看是否有其他用户（或自己以前）完整上传过该文件
        FileMetadata existingFile = this.getOne(
                new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getFileMd5, md5)
        );

        boolean ragReady = ragDocumentService.findByContentHash(md5)
                .map(d -> Objects.equals(1, d.getParseStatus()))
                .orElse(false);

        if (existingFile != null) {
            Long currentUserId = UserHolder.getUser().getId();
            log.info("触发秒传逻辑，文件 MD5: {}, 用户 ID: {}, ragReady={}", md5, currentUserId, ragReady);
            Map<String, Object> instant = new HashMap<>();
            instant.put("instantUpload", true);
            instant.put("message", "秒传成功");
            instant.put("ragReady", ragReady);
            return Result.ok(instant);
        }

        // 2. 【断点续传场景】如果数据库没有，去 Redis 检查是否有已上传的分片
        String redisKey = "upload:progress:" + md5;
        Set<String> uploadedChunks = stringRedisTemplate.opsForSet().members(redisKey);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("uploadedChunks", uploadedChunks == null ? Collections.emptySet() : uploadedChunks);
        resultMap.put("instantUpload", false);
        resultMap.put("ragReady", ragReady);

        return Result.ok(resultMap);
    }

    @Override
    public Result uploadChunk(MultipartFile file, String md5, Integer index) throws IOException {
        // 1. 创建临时分片目录：resources/file/temp/{md5}/
        String tempPath = ResourceUtils.getURL("classpath:").getPath() + "file/temp/" + md5;
        File dir = new File(tempPath);
        if (!dir.exists()) dir.mkdirs();

        // 2. 保存分片文件
        File chunkFile = new File(dir, String.valueOf(index));
        file.transferTo(chunkFile);

        // 3. Redis 记录进度
        stringRedisTemplate.opsForSet().add("upload:progress:" + md5, String.valueOf(index));

        return Result.ok();
    }

    @Override
    public Result mergeChunks(String md5, String fileName) throws IOException{
        // 1. 确定最终存放路径：resources/file/{fileName}
        String rootPath = ResourceUtils.getURL("classpath:").getPath() + "file/";
        File finalFile = new File(rootPath, fileName);

        // 2. 获取临时分片目录
        File tempDir = new File(rootPath + "temp/" + md5);
        File[] chunks = tempDir.listFiles();

        if (chunks == null || chunks.length == 0) {
            return Result.fail("找不到分片文件");
        }

        // 3. 排序并合并（必须按文件名数字从小到大排，否则文件会损坏）
        Arrays.sort(chunks, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

        try (RandomAccessFile raf = new RandomAccessFile(finalFile, "rw")) {
            for (File chunk : chunks) {
                byte[] bytes = FileUtils.readFileToByteArray(chunk);
                raf.write(bytes);
            }
        }

        // 4. 后续处理：存入数据库元数据 (此处调用你的 Mapper 即可)
        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(fileName);
        metadata.setFileMd5(md5);
        metadata.setFileSize(finalFile.length());
        metadata.setFileSuffix(FileUtil.extName(fileName));
        metadata.setFilePath(finalFile.getAbsolutePath());
        metadata.setCreateTime(LocalDateTime.now());

        // 【核心修改】从 UserHolder 获取当前用户 ID
        Long currentUserId = UserHolder.getUser().getId();

        if (currentUserId == null) {
            throw new RuntimeException("用户未登录，禁止操作");
        }

        metadata.setUserId(currentUserId);

        this.save(metadata);

        // 5. 合并成功后：按 MD5 幂等触发 RAG（摘要提纲 + 分块入库），已存在且成功则跳过
        try {
            ragIngestionService.scheduleIngestIfNeeded(
                    md5,
                    currentUserId,
                    finalFile.getAbsolutePath(),
                    fileName,
                    FileUtil.extName(fileName)
            );
        } catch (Exception ex) {
            log.error("RAG 调度失败（文件已保存），md5={}", md5, ex);
        }

        // 6. 清理碎片与 Redis
        FileUtils.deleteDirectory(tempDir);
        stringRedisTemplate.delete("upload:progress:" + md5);

        return Result.ok("文件上传成功");
    }
}
