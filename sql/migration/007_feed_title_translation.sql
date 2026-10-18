-- Run after 006. Additive and rerunnable on MySQL 5.7.
SET @feed_title_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='feed_entry' AND COLUMN_NAME='translated_title')=0,
  'ALTER TABLE feed_entry ADD COLUMN translated_title varchar(500) DEFAULT NULL AFTER title',
  'SELECT 1');
PREPARE feed_title_stmt FROM @feed_title_ddl;
EXECUTE feed_title_stmt;
DEALLOCATE PREPARE feed_title_stmt;

SET @feed_title_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article_source' AND COLUMN_NAME='source_translated_title')=0,
  'ALTER TABLE article_source ADD COLUMN source_translated_title varchar(500) DEFAULT NULL AFTER source_title',
  'SELECT 1');
PREPARE feed_title_stmt FROM @feed_title_ddl;
EXECUTE feed_title_stmt;
DEALLOCATE PREPARE feed_title_stmt;
