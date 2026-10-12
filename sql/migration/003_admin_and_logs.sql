-- MySQL 5.7+ / utf8mb4. Idempotent additive migration: existing user rows retain defaults;
-- rerunning skips columns that already exist and leaves existing search/ask logs untouched.
-- Execute against the selected schema (for this project: big_event).

SET @has_user_type = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'user_type'
);
SET @ddl_user_type = IF(
    @has_user_type = 0,
    'ALTER TABLE `user` ADD COLUMN `user_type` TINYINT NOT NULL DEFAULT 0 COMMENT ''0-human 1-machine author'' AFTER `role`',
    'SELECT ''user.user_type already exists'''
);
PREPARE stmt_user_type FROM @ddl_user_type;
EXECUTE stmt_user_type;
DEALLOCATE PREPARE stmt_user_type;

SET @has_login_enabled = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'login_enabled'
);
SET @ddl_login_enabled = IF(
    @has_login_enabled = 0,
    'ALTER TABLE `user` ADD COLUMN `login_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''0-disabled 1-enabled'' AFTER `status`',
    'SELECT ''user.login_enabled already exists'''
);
PREPARE stmt_login_enabled FROM @ddl_login_enabled;
EXECUTE stmt_login_enabled;
DEALLOCATE PREPARE stmt_login_enabled;

CREATE TABLE IF NOT EXISTS `search_log` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`      bigint unsigned NOT NULL COMMENT '搜索用户',
  `keyword`      varchar(100)    NOT NULL DEFAULT '' COMMENT '关键词',
  `category_id`  bigint unsigned          DEFAULT NULL,
  `time_range`   varchar(16)     NOT NULL DEFAULT 'all' COMMENT 'all/1d/7d/30d',
  `result_count` int             NOT NULL DEFAULT 0,
  `elapsed_ms`   int             NOT NULL DEFAULT 0,
  `create_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_time` (`user_id`, `create_time`),
  INDEX `idx_keyword` (`keyword`),
  INDEX `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='普通搜索日志';

CREATE TABLE IF NOT EXISTS `ask_log` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`         bigint unsigned NOT NULL,
  `question`        varchar(200)    NOT NULL DEFAULT '' COMMENT '用户问题（最多200字）',
  `answer_len`      int             NOT NULL DEFAULT 0,
  `reference_count` int             NOT NULL DEFAULT 0,
  `elapsed_ms`      int             NOT NULL DEFAULT 0,
  `ttft_ms`         int             NOT NULL DEFAULT 0 COMMENT '首字延迟，未知填0',
  `model`           varchar(64)     NOT NULL DEFAULT '',
  `success`         tinyint         NOT NULL DEFAULT 1,
  `create_time`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_time` (`user_id`, `create_time`),
  INDEX `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI问答日志';

-- Verify: SHOW COLUMNS FROM `user` LIKE 'user_type';
--         SHOW COLUMNS FROM `user` LIKE 'login_enabled';
--         SHOW TABLES LIKE 'search_log'; SHOW TABLES LIKE 'ask_log';
