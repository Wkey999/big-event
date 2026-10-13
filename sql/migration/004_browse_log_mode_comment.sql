-- Synchronize the browse_log.mode comment without changing the already-applied migration 002.
-- Safe to rerun; run migration 002 first if the column does not exist.
SET @needs_mode_comment_update = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'browse_log'
      AND COLUMN_NAME = 'mode'
      AND COLUMN_COMMENT <> '曝光来源：latest/recommend/search/ask'
);
SET @ddl_mode_comment = IF(
    @needs_mode_comment_update > 0,
    'ALTER TABLE `browse_log` MODIFY COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT ''latest'' COMMENT ''曝光来源：latest/recommend/search/ask'' AFTER `dwell_ms`',
    'SELECT ''browse_log.mode comment already synchronized'''
);
PREPARE stmt_mode_comment FROM @ddl_mode_comment;
EXECUTE stmt_mode_comment;
DEALLOCATE PREPARE stmt_mode_comment;
