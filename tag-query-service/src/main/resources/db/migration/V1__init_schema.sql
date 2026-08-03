-- ============================================================
-- Flyway V1：初始化 8 张业务表
-- 项目启动时自动执行（如果表不存在就建表）
-- 命名规则：V + 序号 + __ + 描述.sql（注意是两个下划线）
-- ============================================================

-- ----- 1. app_key：调用方身份表 -----
CREATE TABLE IF NOT EXISTS app_key (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    app_key     VARCHAR(64)  NOT NULL COMMENT 'AppKey 标识',
    app_name    VARCHAR(128) NOT NULL COMMENT '调用方名称',
    app_secret  VARCHAR(256) NOT NULL COMMENT '签名密钥（加密存储）',
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用, 1=启用',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用方身份表';

-- ----- 2. data_source：数据源表 -----
CREATE TABLE IF NOT EXISTS data_source (
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    source_id     VARCHAR(64)  NOT NULL COMMENT '数据源标识（如 ds_lead_scoring_001）',
    source_name   VARCHAR(128) NOT NULL COMMENT '数据源名称',
    customer_name VARCHAR(128) NOT NULL COMMENT '所属客户',
    status        TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用, 1=启用',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_id (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

-- ----- 3. app_key_data_source：AppKey ↔ 数据源授权映射 -----
CREATE TABLE IF NOT EXISTS app_key_data_source (
    id             BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    app_key_id     BIGINT NOT NULL COMMENT 'AppKey ID',
    data_source_id BIGINT NOT NULL COMMENT '数据源 ID',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_key_data_source (app_key_id, data_source_id),
    FOREIGN KEY (app_key_id) REFERENCES app_key(id),
    FOREIGN KEY (data_source_id) REFERENCES data_source(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AppKey数据源授权映射表';

-- ----- 4. pull_config：拉取配置表 -----
CREATE TABLE IF NOT EXISTS pull_config (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    customer_name   VARCHAR(128) NOT NULL COMMENT '客户名称',
    data_source_id  VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    s3_endpoint     VARCHAR(256) NOT NULL COMMENT 'S3 地址',
    s3_bucket       VARCHAR(128) NOT NULL COMMENT 'S3 Bucket',
    s3_prefix       VARCHAR(256) NOT NULL COMMENT 'S3 路径前缀',
    cron_expression VARCHAR(64)  NOT NULL COMMENT '拉取周期（cron）',
    partition_rule  VARCHAR(32)  NOT NULL COMMENT '分区规则：LATEST / CURRENT_MONTH',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用, 1=启用',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拉取配置表';

-- ----- 5. pull_task：拉取任务表 -----
CREATE TABLE IF NOT EXISTS pull_task (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    pull_config_id  BIGINT NOT NULL COMMENT '关联拉取配置',
    s3_full_path    VARCHAR(512) NOT NULL COMMENT '实际拉取的完整 S3 路径',
    partition_name  VARCHAR(128) NOT NULL COMMENT '目标分区',
    trigger_type    VARCHAR(16)  NOT NULL COMMENT '触发方式：SCHEDULED / MANUAL',
    status          VARCHAR(32)  NOT NULL COMMENT '状态：PENDING / PULLING / PULLED / FAILED',
    file_count      INT DEFAULT 0 COMMENT '拉取文件数',
    file_total_size BIGINT DEFAULT 0 COMMENT '文件总大小 (bytes)',
    failure_reason  TEXT COMMENT '失败原因',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '结束时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    FOREIGN KEY (pull_config_id) REFERENCES pull_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拉取任务表';

-- ----- 6. integration_task：数据集成任务表 -----
CREATE TABLE IF NOT EXISTS integration_task (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    pull_task_id    BIGINT NOT NULL COMMENT '关联拉取任务',
    data_source_id  VARCHAR(64) NOT NULL COMMENT '数据源标识',
    status          VARCHAR(32) NOT NULL COMMENT '状态：PENDING / PARSING / VALIDATING / LOADING / SUCCESS / FAILED',
    total_records   BIGINT DEFAULT 0 COMMENT '总记录数',
    valid_records   BIGINT DEFAULT 0 COMMENT '有效记录数',
    duplicate_rate  DECIMAL(5,4) DEFAULT 0 COMMENT '重复率',
    null_rate       DECIMAL(5,4) DEFAULT 0 COMMENT '空值率',
    failure_reason  TEXT COMMENT '失败原因',
    started_at      DATETIME COMMENT '开始时间',
    finished_at     DATETIME COMMENT '结束时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    FOREIGN KEY (pull_task_id) REFERENCES pull_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集成任务表';

-- ----- 7. data_version：数据版本表 -----
CREATE TABLE IF NOT EXISTS data_version (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    version_key            VARCHAR(128) NOT NULL COMMENT '版本标识，如 v_ds_001_20260725_01',
    data_source_id         VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    integration_task_id    BIGINT NOT NULL COMMENT '关联集成任务',
    record_count           BIGINT NOT NULL COMMENT '包含记录数',
    validate_status        VARCHAR(32)  NOT NULL COMMENT '校验状态：PENDING / PASSED / FAILED',
    active_status          VARCHAR(32)  NOT NULL COMMENT '生效状态：INACTIVE / ACTIVE / DEPRECATED',
    validate_failure_reason TEXT COMMENT '校验失败原因',
    activated_at           DATETIME COMMENT '生效时间',
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_key (version_key),
    FOREIGN KEY (integration_task_id) REFERENCES integration_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据版本表';

-- ----- 8. version_event_log：版本操作日志表 -----
CREATE TABLE IF NOT EXISTS version_event_log (
    id                BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    data_source_id    VARCHAR(64)  NOT NULL COMMENT '数据源标识',
    action            VARCHAR(32)  NOT NULL COMMENT '操作类型：ACTIVATE / ROLLBACK',
    from_version_key  VARCHAR(128) COMMENT '操作前版本',
    to_version_key    VARCHAR(128) COMMENT '操作后版本',
    result            VARCHAR(32)  NOT NULL COMMENT '结果：SUCCESS / FAILED',
    reason            TEXT COMMENT '操作原因/失败原因',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本操作日志表';
