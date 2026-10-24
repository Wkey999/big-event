-- Repeatable MySQL 5.7 migration. Historical pending entries are never opted in.
SET @feed_ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='feed_entry' AND COLUMN_NAME='auto_publish_requested')=0,
 'ALTER TABLE feed_entry ADD COLUMN auto_publish_requested TINYINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feed_stmt FROM @feed_ddl; EXECUTE feed_stmt; DEALLOCATE PREPARE feed_stmt;
SET @feed_ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='feed_entry' AND COLUMN_NAME='publish_mode')=0,
 'ALTER TABLE feed_entry ADD COLUMN publish_mode TINYINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feed_stmt FROM @feed_ddl; EXECUTE feed_stmt; DEALLOCATE PREPARE feed_stmt;
SET @feed_ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='feed_run' AND COLUMN_NAME='auto_published')=0,
 'ALTER TABLE feed_run ADD COLUMN auto_published INT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feed_stmt FROM @feed_ddl; EXECUTE feed_stmt; DEALLOCATE PREPARE feed_stmt;
SET @feed_ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='feed_run' AND COLUMN_NAME='auto_pending')=0,
 'ALTER TABLE feed_run ADD COLUMN auto_pending INT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feed_stmt FROM @feed_ddl; EXECUTE feed_stmt; DEALLOCATE PREPARE feed_stmt;
