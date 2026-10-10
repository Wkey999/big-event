-- 为已有浏览记录标注信息流模式；旧数据以 latest 作为默认归因。
-- 执行前备份数据库；该 ALTER 不删除或重写浏览记录。
ALTER TABLE browse_log
    ADD COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT 'latest'
        COMMENT '曝光来源：latest/recommend' AFTER dwell_ms,
    ADD INDEX idx_mode_time (`mode`, create_time);

-- 验收：SHOW COLUMNS FROM browse_log LIKE 'mode';
--       SHOW INDEX FROM browse_log; 应包含 idx_mode_time。
