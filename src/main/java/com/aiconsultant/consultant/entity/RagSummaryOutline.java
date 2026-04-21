package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 摘要提纲；「每 chunk 仅一条可用」由 {@link com.aiconsultant.consultant.service.RagSummaryOutlineService#activateSummary(Long)} 保证。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("rag_summary_outline")
public class RagSummaryOutline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Long chapterId;
    private Long chunkId;

    private String outlineText;
    private String outlineFormat;

    private String embeddingModel;
    /** 0未生成 1成功 2失败 */
    private Integer vectorStatus;
    private String vectorRef;
    private String bm25IndexKey;

    private Integer version;
    /** 是否当前生效；同一 chunk 仅允许一条为 1（业务层切换） */
    private Integer isAvailable;

    private String regenReason;

    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
