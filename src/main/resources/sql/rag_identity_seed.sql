-- 可选：初始化身份字典与各专家默认身份绑定（执行前请先执行 rag_identity_permission_schema.sql）
-- 可按业务增删；以下为示例编码，便于与 rag_document_identity / rag_user_identity 对齐

SET NAMES utf8mb4;

INSERT INTO `rag_sec_identity` (`id`, `code`, `display_name`, `description`)
VALUES
  (1, 'NET_OPS', '内网运维域', '内部网络、基础设施类知识'),
  (2, 'DEV_STD', '开发规范域', '开发指南、工程规范类知识'),
  (3, 'HR_POLICY', '人力合规域', '员工守则、制度合规类知识'),
  (4, 'GENERAL_KB', '通用知识库', '各专家可共读的通用资料')
ON DUPLICATE KEY UPDATE
  `display_name` = VALUES(`display_name`),
  `description` = VALUES(`description`),
  `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `rag_agent_identity` (`agent_code`, `identity_id`)
VALUES
  ('INTERNAL_NETWORK', 1),
  ('INTERNAL_NETWORK', 4),
  ('DEV_GUIDE', 2),
  ('DEV_GUIDE', 4),
  ('EMPLOYEE_POLICY', 3),
  ('EMPLOYEE_POLICY', 4)
ON DUPLICATE KEY UPDATE `identity_id` = VALUES(`identity_id`);

-- 为测试用户绑定身份示例（请替换 user_id）
-- INSERT INTO `rag_user_identity` (`user_id`, `identity_id`) VALUES (1, 1), (1, 2), (1, 3), (1, 4);
