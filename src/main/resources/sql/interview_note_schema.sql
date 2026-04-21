-- 面试笔记总结员落库（与 RAG 无关，供 Agent Tool 写入）

CREATE TABLE IF NOT EXISTS `interview_note_summary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `session_id` BIGINT NULL DEFAULT NULL,
  `title` VARCHAR(512) NOT NULL DEFAULT '',
  `content` LONGTEXT NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `is_available` TINYINT NOT NULL DEFAULT 1,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_session` (`user_id`, `session_id`),
  KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试笔记总结沉淀';
