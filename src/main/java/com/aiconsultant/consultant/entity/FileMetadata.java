package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_metadata")
public class FileMetadata {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileSuffix; // 文件格式/后缀
    private Long fileSize;    // 总大小
    private String fileMd5;   // 文件唯一标识，用于秒传
    private String filePath;  // 最终存储路径
    private LocalDateTime createTime;
}
