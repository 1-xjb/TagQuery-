ALTER TABLE app_key_data_source DROP FOREIGN KEY app_key_data_source_ibfk_2;
ALTER TABLE app_key_data_source MODIFY COLUMN data_source_id VARCHAR(64) NOT NULL COMMENT '数据源业务标识 source_id';
INSERT IGNORE INTO app_key_data_source (id, app_key_id, data_source_id) VALUES (1, 1, 'ds_lead_scoring_001');