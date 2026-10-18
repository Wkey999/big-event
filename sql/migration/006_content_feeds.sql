-- Additive, rerunnable migration for MySQL 5.7+. Never run big_event_init.sql on an existing database.
CREATE TABLE IF NOT EXISTS feed_entry (
  id bigint unsigned NOT NULL AUTO_INCREMENT,
  source_key varchar(30) NOT NULL,
  source_name varchar(60) NOT NULL,
  source_url varchar(1000) NOT NULL,
  url_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title varchar(500) NOT NULL,
  source_author varchar(200) NOT NULL DEFAULT '',
  source_published_at datetime DEFAULT NULL,
  fetched_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0-pending 1-published 2-rejected',
  article_id bigint unsigned DEFAULT NULL,
  reviewed_by bigint unsigned DEFAULT NULL,
  reviewed_at datetime DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_feed_url (url_hash),
  UNIQUE KEY uk_feed_article (article_id),
  KEY idx_feed_review (status, id),
  KEY idx_feed_source (source_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS article_source (
  article_id bigint unsigned NOT NULL,
  source_name varchar(60) NOT NULL,
  source_url varchar(1000) NOT NULL,
  source_title varchar(500) NOT NULL,
  source_author varchar(200) NOT NULL DEFAULT '',
  source_published_at datetime DEFAULT NULL,
  source_fetched_at datetime NOT NULL,
  PRIMARY KEY (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feed_run (
  id bigint unsigned NOT NULL AUTO_INCREMENT,
  source_key varchar(30) NOT NULL,
  started_at datetime NOT NULL,
  finished_at datetime NOT NULL,
  scanned int NOT NULL DEFAULT 0,
  inserted int NOT NULL DEFAULT 0,
  duplicates int NOT NULL DEFAULT 0,
  filtered int NOT NULL DEFAULT 0,
  success tinyint NOT NULL,
  message varchar(200) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  KEY idx_feed_run (source_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
