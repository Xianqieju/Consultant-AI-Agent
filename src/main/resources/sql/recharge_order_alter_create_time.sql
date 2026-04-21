-- 充值订单补偿扫描依赖 create_time；若表已有该列可跳过。
ALTER TABLE recharge_order
    ADD COLUMN create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER version,
    ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time;

-- 建议索引（按状态 + 时间拉取待处理批次，避免全表扫）
-- CREATE INDEX idx_recharge_order_status_create ON recharge_order (status, create_time);
