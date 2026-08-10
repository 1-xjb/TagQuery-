-- V5：权限失败日志表（PDF 3.5 要求：AppKey、dataSourceId、权限校验结果、失败原因、来源 IP）
CREATE TABLE IF NOT EXISTS authz_failure_log (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    app_key          VARCHAR(64) NOT NULL COMMENT '调用方身份',
    data_source_id   VARCHAR(64) NOT NULL COMMENT '越权访问的目标数据源',
    authz_passed     TINYINT COMMENT '权限校验结果：0=失败',
    failure_reason   VARCHAR(256) NOT NULL COMMENT '失败原因',
    source_ip        VARCHAR(64) NOT NULL COMMENT '来源 IP（安全审计）',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限失败日志';
