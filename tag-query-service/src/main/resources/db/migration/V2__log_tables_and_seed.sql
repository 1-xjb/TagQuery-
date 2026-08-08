-- V2：日志表 + qps_limit 列 + 测试种子数据
-- 开发期可重复执行（INSERT IGNORE 幂等）

-- 1. app_key 增加限流配置列
ALTER TABLE app_key ADD COLUMN qps_limit INT NOT NULL DEFAULT 100 COMMENT '每秒请求上限';

-- 2. query_log：查询调用日志
CREATE TABLE IF NOT EXISTS query_log (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    request_id     VARCHAR(64) NOT NULL,
    app_key        VARCHAR(64) NOT NULL,
    data_source_id VARCHAR(64) NOT NULL,
    id_count       INT NOT NULL,
    hit_count      INT NOT NULL,
    cost_ms        BIGINT NOT NULL,
    status         VARCHAR(32) NOT NULL,
    version_key    VARCHAR(128),
    source_ip      VARCHAR(64),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_request_id (request_id),
    KEY idx_app_key_created (app_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. auth_failure_log：鉴权失败日志
CREATE TABLE IF NOT EXISTS auth_failure_log (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    app_key          VARCHAR(64),
    signature_passed TINYINT,
    timestamp_passed TINYINT,
    failure_reason   VARCHAR(256) NOT NULL,
    source_ip        VARCHAR(64) NOT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. rate_limit_log：限流日志
CREATE TABLE IF NOT EXISTS rate_limit_log (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    app_key       VARCHAR(64) NOT NULL,
    dimension     VARCHAR(32) NOT NULL,
    threshold_val INT NOT NULL,
    current_value INT NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 种子数据
INSERT IGNORE INTO app_key (id, app_key, app_name, app_secret, status, qps_limit)
VALUES (1, 'test_app', '测试调用方', 'dev_secret_123456', 1, 100);

INSERT IGNORE INTO data_source (id, source_id, source_name, customer_name, status)
VALUES (1, 'ds_lead_scoring_001', '线索评级标签源', '测试客户A', 1);

-- 授权关系在 V3 里插入，此处跳过
