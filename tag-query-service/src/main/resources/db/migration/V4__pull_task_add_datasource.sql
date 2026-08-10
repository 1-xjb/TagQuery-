-- V4：pull_task 冗余数据源标识，免去 join（Day 6）
-- 注意：MySQL 8/9 不支持 ADD COLUMN IF NOT EXISTS，V4 由 Flyway 保证只执行一次
ALTER TABLE pull_task ADD COLUMN data_source_id VARCHAR(64) COMMENT '冗余数据源标识，免去 join';
