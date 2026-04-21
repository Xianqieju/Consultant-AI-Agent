SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user_expert_permission` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `expert_code` VARCHAR(64) NOT NULL COMMENT '与 ExpertDomain 枚举名一致',
  `allowed` TINYINT NOT NULL DEFAULT 1 COMMENT '1允许 0拒绝',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `expert_code`),
  KEY `idx_expert_code` (`expert_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户可调用的技术专家权限';
