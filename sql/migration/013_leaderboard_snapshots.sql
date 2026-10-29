-- Additive, rerunnable MySQL 5.7+ migration. Apply before enabling leaderboard sync.
-- Existing application tables and data are untouched.
CREATE TABLE IF NOT EXISTS leaderboard_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  metric VARCHAR(32) NOT NULL,
  source_date DATE DEFAULT NULL COMMENT 'Arena leaderboard publication date, not model release date',
  synced_at DATETIME(3) NOT NULL,
  sync_day DATE NOT NULL COMMENT 'Calendar day in configured sync time zone',
  content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  item_count SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  KEY idx_leaderboard_snapshot_dimension (dimension, id),
  KEY idx_leaderboard_snapshot_day (dimension, sync_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS leaderboard_snapshot_item (
  snapshot_id BIGINT UNSIGNED NOT NULL,
  model_name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  organization VARCHAR(255) NOT NULL DEFAULT '',
  `license` VARCHAR(255) NOT NULL DEFAULT '',
  rank_number SMALLINT UNSIGNED NOT NULL,
  score DOUBLE NOT NULL,
  published_date DATE DEFAULT NULL,
  newly_listed TINYINT(1) NOT NULL DEFAULT 0,
  previous_rank SMALLINT UNSIGNED DEFAULT NULL,
  rank_change SMALLINT DEFAULT NULL COMMENT 'Previous rank minus current rank; positive means improvement',
  rank_trend VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  PRIMARY KEY (snapshot_id, model_name),
  KEY idx_leaderboard_item_rank (snapshot_id, rank_number),
  CONSTRAINT fk_leaderboard_item_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES leaderboard_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS leaderboard_sync_state (
  dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  last_attempt_at DATETIME(3) DEFAULT NULL,
  last_attempt_day DATE DEFAULT NULL,
  attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  last_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-never 1-success 2-failure',
  last_summary VARCHAR(200) NOT NULL DEFAULT '',
  last_success_snapshot_id BIGINT UNSIGNED DEFAULT NULL,
  last_success_at DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (dimension),
  KEY idx_leaderboard_state_snapshot (last_success_snapshot_id),
  CONSTRAINT fk_leaderboard_state_snapshot FOREIGN KEY (last_success_snapshot_id)
    REFERENCES leaderboard_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
