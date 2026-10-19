-- MySQL 5.7-compatible and repeatable.
SET @add_feed_skipped = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
   AND TABLE_NAME='feed_run' AND COLUMN_NAME='skipped')=0,
  'ALTER TABLE feed_run ADD COLUMN skipped TINYINT NOT NULL DEFAULT 0 AFTER success', 'SELECT 1');
PREPARE feed_skipped_stmt FROM @add_feed_skipped;
EXECUTE feed_skipped_stmt;
DEALLOCATE PREPARE feed_skipped_stmt;
UPDATE feed_run SET success=0,skipped=1
WHERE scanned=0 AND inserted=0 AND message IN ('今日采集总量已达上限','该来源今日采集数量已达上限');
