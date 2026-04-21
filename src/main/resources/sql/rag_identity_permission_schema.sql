-- RAG 资产身份权限：新表独立部署，不修改 rag_document 等现有表结构
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `rag_sec_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '身份ID',
  `code` VARCHAR(64) NOT NULL COMMENT '稳定编码，如 NET_OPS',
  `display_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名',
  `description` VARCHAR(512) NOT NULL DEFAULT '' COMMENT '说明',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_sec_identity_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 安全身份字典';

CREATE TABLE IF NOT EXISTS `rag_document_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL COMMENT 'rag_document.id',
  `identity_id` BIGINT NOT NULL COMMENT 'rag_sec_identity.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_identity` (`document_id`, `identity_id`),
  KEY `idx_rag_document_identity_identity` (`identity_id`),
  KEY `idx_rag_document_identity_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档绑定的资产身份（多对多）';

CREATE TABLE IF NOT EXISTS `rag_user_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `identity_id` BIGINT NOT NULL COMMENT 'rag_sec_identity.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_identity` (`user_id`, `identity_id`),
  KEY `idx_rag_user_identity_identity` (`identity_id`),
  KEY `idx_rag_user_identity_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主体身份';

CREATE TABLE IF NOT EXISTS `rag_agent_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `agent_code` VARCHAR(64) NOT NULL COMMENT '与 ExpertDomain 枚举名一致',
  `identity_id` BIGINT NOT NULL COMMENT 'rag_sec_identity.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_identity` (`agent_code`, `identity_id`),
  KEY `idx_rag_agent_identity_identity` (`identity_id`),
  KEY `idx_rag_agent_identity_agent` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专家 Agent 绑定的主体身份';
