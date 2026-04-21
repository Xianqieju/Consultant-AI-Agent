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
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    /** pdf / docx / html ... */
    private String sourceType;
    private String storageUri;
    private String contentHash;
    private String language;
    /** 0未知 1教学相关 2非教学 */
    private Integer eduRelevance;
    /** 0解析中 1成功 2失败 */
    private Integer parseStatus;
    private Integer version;
    /** 1可用 0不可用 */
    private Integer isAvailable;

    /**
     * 技术专家领域标签，与 {@code ExpertDomain} 枚举名一致；空或 GENERAL 表示各专家均可检索。
     */
    private String expertScope;

    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
