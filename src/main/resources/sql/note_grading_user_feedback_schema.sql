CREATE TABLE IF NOT EXISTS note_grading_user_feedback (
    id               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    session_id       BIGINT       NULL COMMENT '会话ID（可选）',
    pipeline_id      VARCHAR(64)  NULL COMMENT '批改流水线ID（可选）',
    graded_excerpt   VARCHAR(4000) NULL COMMENT '用户摘录的批改输出片段',
    user_comment     VARCHAR(2000) NOT NULL COMMENT '用户反馈正文',
    agent_analysis   TEXT         NOT NULL COMMENT '反馈智能体输出（JSON 或降级文本）',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ngfb_user_time (user_id, create_time),
    KEY idx_ngfb_pipeline (pipeline_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记批改用户文本反馈与智能体分析';
