-- Additive MySQL 5.7-compatible migration. Reservations survive reloads and restarts.
CREATE TABLE IF NOT EXISTS ai_trial_daily_usage (
  usage_date DATE NOT NULL,
  scope_id VARCHAR(64) NOT NULL COMMENT 'site or user:<id>',
  used_count INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (usage_date, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
