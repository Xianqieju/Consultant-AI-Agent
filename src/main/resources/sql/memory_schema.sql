-- 会话级记忆：摘要（乐观锁）、画像（无版本历史 v1）、摘要任务发件箱（弱一致）

CREATE TABLE IF NOT EXISTS chat_session_summary (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    session_id      BIGINT       NOT NULL COMMENT '会话 ID（与 Redis ChatMemory memoryId 一致）',
    user_id         BIGINT       NOT NULL COMMENT '用户 ID（冗余，便于运维查询）',
    summary_text    MEDIUMTEXT   NULL COMMENT '累积会话摘要正文',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chat_session_summary_session (session_id),
    KEY idx_chat_session_summary_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话历史摘要（仅当前会话）';

CREATE TABLE IF NOT EXISTS chat_session_memory_profile (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    user_id         BIGINT       NOT NULL,
    session_id      BIGINT       NOT NULL,
    profile_json    JSON         NULL COMMENT '画像 JSON（含固定键 + ext）',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（更新画像用）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_memory_profile_user_session (user_id, session_id),
    KEY idx_memory_profile_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话画像（v1 不做历史版本）';

CREATE TABLE IF NOT EXISTS user_memory_profile (
    id                  BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    user_id             BIGINT       NOT NULL COMMENT '用户 ID',
    profile_phrases_json JSON        NULL COMMENT '用户级短语画像 JSON（tone/depth/topics/avoid/ext）',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_memory_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户级短语画像';

CREATE TABLE IF NOT EXISTS memory_summary_outbox (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    session_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    task_type       VARCHAR(16)  NOT NULL DEFAULT 'SUMMARY' COMMENT 'SUMMARY/PROFILE/BOTH',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=PROCESSING 2=DONE 3=FAILED',
    retry_count     INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(512) NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_memory_outbox_session_status (session_id, status),
    KEY idx_memory_outbox_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话摘要异步任务发件箱';
