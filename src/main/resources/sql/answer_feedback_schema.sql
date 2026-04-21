CREATE TABLE IF NOT EXISTS answer_feedback (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    session_id      BIGINT       NOT NULL COMMENT '会话ID',
    message_id      BIGINT       NOT NULL COMMENT '被评价的AI消息ID(chat_message.id)',
    attitude        TINYINT      NOT NULL COMMENT '1=点赞,-1=点踩',
    reason_tag      VARCHAR(64)  NULL COMMENT '反馈标签，如too_long/off_topic',
    reason_text     VARCHAR(512) NULL COMMENT '用户补充说明',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_feedback_user_message (user_id, message_id),
    KEY idx_feedback_session (session_id),
    KEY idx_feedback_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户对AI回答的点赞/点踩态度表';
