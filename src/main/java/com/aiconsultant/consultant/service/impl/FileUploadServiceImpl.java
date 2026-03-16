package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.FileMetadata;
import com.aiconsultant.consultant.mapper.FileMetadataMapper;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.FileUploadService;
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

@Service
@Slf4j
public class FileUploadServiceImpl extends ServiceImpl<FileMetadataMapper, FileMetadata> implements FileUploadService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Result checkUpload(String md5, String fileName, Integer totalChunks) {
        // 1. 检查数据库，看是否有其他用户（或自己以前）完整上传过该文件
        FileMetadata existingFile = this.getOne(
                new LambdaQueryWrapper<FileMetadata>().eq(FileMetadata::getFileMd5, md5)
        );

        if (existingFile != null) {
            // 【秒传场景】数据库已有记录，直接为当前用户关联一份元数据
            Long currentUserId = UserHolder.getUser().getId();

            // 如果当前用户还没关联过这个文件，则新增一条记录（指向同一个物理路径）
            // 这里可以根据业务需求决定是直接返回成功，还是在数据库新插一条 user_id 不同的记录
            log.info("触发秒传逻辑，文件 MD5: {}, 用户 ID: {}", md5, currentUserId);
            return Result.ok("秒传成功");
        }

        // 2. 【断点续传场景】如果数据库没有，去 Redis 检查是否有已上传的分片
        String redisKey = "upload:progress:" + md5;
        Set<String> uploadedChunks = stringRedisTemplate.opsForSet().members(redisKey);

        // 3. 返回给前端已经存在的分片索引列表
        // 前端收到后，会对比自己手中的分片，只发送那些不在列表中的分片
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("uploadedChunks", uploadedChunks); // 比如 ["1", "2", "5"]
        resultMap.put("isUploaded", false);

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

        // 5. 清理碎片与 Redis
        FileUtils.deleteDirectory(tempDir);
        stringRedisTemplate.delete("upload:progress:" + md5);

        return Result.ok("文件上传成功");
    }
}
