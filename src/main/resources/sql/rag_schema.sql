-- RAG：父文档 → 章节 → 内容块 → 摘要提纲（多版本）
-- 兼容 MySQL 5.7：「同一 chunk 仅一条 is_available=1」由业务层保证（见 RagSummaryOutlineService#activateSummary）
-- 字符集可按库统一调整

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `rag_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '父文档ID',
  `user_id` BIGINT NOT NULL COMMENT '归属用户',
  `title` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '标题',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'pdf/docx/html/...',
  `storage_uri` VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '对象存储路径',
  `content_hash` CHAR(64) NOT NULL DEFAULT '' COMMENT '内容指纹',
  `language` VARCHAR(16) NOT NULL DEFAULT 'zh' COMMENT '语言',
  `edu_relevance` TINYINT NOT NULL DEFAULT 0 COMMENT '0未知 1教学相关 2非教学',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0解析中 1成功 2失败',
  `version` INT NOT NULL DEFAULT 1 COMMENT '文档版本',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '1可用 0不可用',
  `expert_scope` VARCHAR(64) NULL DEFAULT NULL COMMENT 'INTERNAL_NETWORK|DEV_GUIDE|EMPLOYEE_POLICY|GENERAL，空=不限',
  `deleted_at` DATETIME NULL DEFAULT NULL COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_content_hash` (`content_hash`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG父文档';

CREATE TABLE IF NOT EXISTS `rag_document_chapter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  `document_id` BIGINT NOT NULL COMMENT '父文档ID',
  `parent_chapter_id` BIGINT NULL DEFAULT NULL COMMENT '父章节',
  `chapter_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '章节序号',
  `chapter_title` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '章节标题',
  `level` TINYINT NOT NULL DEFAULT 1 COMMENT '层级',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `approx_token_count` INT NOT NULL DEFAULT 0 COMMENT '估算token',
  `version` INT NOT NULL DEFAULT 1 COMMENT '结构版本',
  `is_available` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可用',
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_parent_chapter_id` (`parent_chapter_id`),
  KEY `idx_deleted_at` (`deleted_at`),
  CONSTRAINT `fk_rag_chapter_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG章节';

CREATE TABLE IF NOT EXISTS `rag_content_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内容块ID',
  `document_id` BIGINT NOT NULL,
  `chapter_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL DEFAULT 0 COMMENT '章内序号',
  `chunk_type` VARCHAR(16) NOT NULL DEFAULT 'PARAGRAPH' COMMENT '块类型',
  `content_text` LONGTEXT NOT NULL,
  `token_count` INT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 1 COMMENT '块版本',
  `is_available` TINYINT NOT NULL DEFAULT 1,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chapter_chunk` (`chapter_id`, `chunk_index`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_deleted_at` (`deleted_at`),
  CONSTRAINT `fk_rag_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document` (`id`),
  CONSTRAINT `fk_rag_chunk_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `rag_document_chapter` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG内容块';

CREATE TABLE IF NOT EXISTS `rag_summary_outline` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '摘要ID',
  `document_id` BIGINT NOT NULL,
  `chapter_id` BIGINT NOT NULL,
  `chunk_id` BIGINT NOT NULL,
  `outline_text` LONGTEXT NOT NULL COMMENT '摘要提纲',
  `outline_format` VARCHAR(32) NOT NULL DEFAULT 'MARKDOWN',
  `embedding_model` VARCHAR(64) NOT NULL DEFAULT '',
  `vector_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未生成 1成功 2失败',
  `vector_ref` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '向量侧键',
  `bm25_index_key` VARCHAR(128) NOT NULL DEFAULT '',
  `version` INT NOT NULL DEFAULT 1 COMMENT '摘要版本号，重生递增',
  `is_available` TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前可用（每chunk仅一条为1）',
  `regen_reason` VARCHAR(256) NOT NULL DEFAULT '',
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_chunk_id` (`chunk_id`),
  KEY `idx_chunk_version` (`chunk_id`, `version`),
  KEY `idx_chunk_available` (`chunk_id`, `is_available`),
  KEY `idx_deleted_at` (`deleted_at`),
  CONSTRAINT `fk_rag_summary_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document` (`id`),
  CONSTRAINT `fk_rag_summary_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `rag_document_chapter` (`id`),
  CONSTRAINT `fk_rag_summary_chunk` FOREIGN KEY (`chunk_id`) REFERENCES `rag_content_chunk` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG摘要提纲';
