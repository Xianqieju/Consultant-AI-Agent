package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("memory_summary_outbox")
public class MemorySummaryOutbox {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long sessionId;

    private Long userId;

    /** SUMMARY/PROFILE/BOTH */
    private String taskType;

    /** 0=PENDING 1=PROCESSING 2=DONE 3=FAILED */
    private Integer status;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
