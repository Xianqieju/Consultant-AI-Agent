package com.aiconsultant.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("rag_document_chapter")
public class RagDocumentChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Long parentChapterId;
    private String chapterNo;
    private String chapterTitle;
    private Integer level;
    private Integer sortOrder;
    private Integer approxTokenCount;
    private Integer version;
    private Integer isAvailable;

    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
