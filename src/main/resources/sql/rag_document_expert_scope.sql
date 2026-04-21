-- RAG 文档按技术专家领域过滤（与 ExpertDomain 枚举名一致；空或 GENERAL 表示不限制）
SET NAMES utf8mb4;

ALTER TABLE `rag_document`
    ADD COLUMN `expert_scope` VARCHAR(64) NULL DEFAULT NULL COMMENT 'INTERNAL_NETWORK|DEV_GUIDE|EMPLOYEE_POLICY|GENERAL，空=不限' AFTER `is_available`;
