-- MySQL 5.7+ / utf8mb4. Community schema; safe to rerun.
CREATE TABLE IF NOT EXISTS `post_media` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `article_id`  bigint unsigned NOT NULL,
  `url`         varchar(255)    NOT NULL,
  `media_type`  tinyint         NOT NULL DEFAULT 1 COMMENT '1-图片 2-视频',
  `sort_order`  int             NOT NULL DEFAULT 0,
  `width`       int                      DEFAULT NULL,
  `height`      int                      DEFAULT NULL,
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_article` (`article_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子媒体';

CREATE TABLE IF NOT EXISTS `topic` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `name`        varchar(30)     NOT NULL,
  `post_count`  int unsigned    NOT NULL DEFAULT 0 COMMENT '冗余计数',
  `hot_score`   int unsigned    NOT NULL DEFAULT 0 COMMENT '热度分，可定时刷新',
  `status`      tinyint         NOT NULL DEFAULT 1 COMMENT '0-隐藏 1-正常',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name` (`name`),
  INDEX `idx_hot` (`status`, `hot_score` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题';

CREATE TABLE IF NOT EXISTS `post_topic` (
  `article_id`  bigint unsigned NOT NULL,
  `topic_id`    bigint unsigned NOT NULL,
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`article_id`, `topic_id`),
  INDEX `idx_topic` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子话题关系';

CREATE TABLE IF NOT EXISTS `comment` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `article_id`  bigint unsigned NOT NULL,
  `user_id`     bigint unsigned NOT NULL,
  `root_id`     bigint unsigned          DEFAULT NULL COMMENT '楼层根评论 id，顶层为 NULL',
  `parent_id`   bigint unsigned          DEFAULT NULL COMMENT '被回复的评论 id',
  `content`     varchar(500)    NOT NULL,
  `like_count`  int unsigned    NOT NULL DEFAULT 0,
  `reply_count` int unsigned    NOT NULL DEFAULT 0,
  `state`       tinyint         NOT NULL DEFAULT 1 COMMENT '0-隐藏 1-正常',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     tinyint         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_article_time` (`article_id`, `deleted`, `create_time`),
  INDEX `idx_root` (`root_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论';

CREATE TABLE IF NOT EXISTS `user_follow` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `follower_id` bigint unsigned NOT NULL COMMENT '关注者',
  `followee_id` bigint unsigned NOT NULL COMMENT '被关注者',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_follow` (`follower_id`, `followee_id`),
  INDEX `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注关系';

-- Preserve legacy article covers in the media model. Repeated runs skip already-migrated articles.
INSERT INTO `post_media` (`article_id`, `url`, `media_type`, `sort_order`)
SELECT a.id, a.cover_img, 1, 0
FROM article a
WHERE a.cover_img <> ''
  AND NOT EXISTS (SELECT 1 FROM post_media pm WHERE pm.article_id = a.id);

-- Upgrade the legacy comment table from big_event_init.sql in place, preserving rows.
-- The project had no comment endpoints before this migration. Legacy parent_id=0 denotes a root.
SET @has_root_id = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='root_id');
SET @ddl_root_id = IF(@has_root_id=0, 'ALTER TABLE `comment` ADD COLUMN `root_id` bigint unsigned DEFAULT NULL COMMENT ''楼层根评论 id，顶层为 NULL'' AFTER `user_id`', 'SELECT ''comment.root_id exists''');
PREPARE stmt_root_id FROM @ddl_root_id; EXECUTE stmt_root_id; DEALLOCATE PREPARE stmt_root_id;

SET @has_reply_count = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='reply_count');
SET @ddl_reply_count = IF(@has_reply_count=0, 'ALTER TABLE `comment` ADD COLUMN `reply_count` int unsigned NOT NULL DEFAULT 0 AFTER `like_count`', 'SELECT ''comment.reply_count exists''');
PREPARE stmt_reply_count FROM @ddl_reply_count; EXECUTE stmt_reply_count; DEALLOCATE PREPARE stmt_reply_count;

SET @has_state = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='state');
SET @ddl_state = IF(@has_state=0, 'ALTER TABLE `comment` ADD COLUMN `state` tinyint NOT NULL DEFAULT 1 COMMENT ''0-隐藏 1-正常'' AFTER `reply_count`', 'SELECT ''comment.state exists''');
PREPARE stmt_state FROM @ddl_state; EXECUTE stmt_state; DEALLOCATE PREPARE stmt_state;

SET @has_status = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='status');
SET @ddl_copy_status = IF(@has_status>0, 'UPDATE `comment` SET `state` = `status`', 'SELECT ''legacy comment.status absent''');
PREPARE stmt_copy_status FROM @ddl_copy_status; EXECUTE stmt_copy_status; DEALLOCATE PREPARE stmt_copy_status;

SET @parent_not_null = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='parent_id' AND IS_NULLABLE='NO');
SET @ddl_parent_nullable = IF(@parent_not_null>0, 'ALTER TABLE `comment` MODIFY COLUMN `parent_id` bigint unsigned DEFAULT NULL COMMENT ''被回复的评论 id''', 'SELECT ''comment.parent_id nullable''');
PREPARE stmt_parent_nullable FROM @ddl_parent_nullable; EXECUTE stmt_parent_nullable; DEALLOCATE PREPARE stmt_parent_nullable;

UPDATE `comment` SET `parent_id` = NULL WHERE `parent_id` = 0;
UPDATE `comment` SET `root_id` = `parent_id` WHERE `root_id` IS NULL AND `parent_id` IS NOT NULL;

SET @content_type = (SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='content');
SET @ddl_content_type = IF(@content_type <> 'varchar(500)', 'ALTER TABLE `comment` MODIFY COLUMN `content` varchar(500) NOT NULL', 'SELECT ''comment.content already varchar(500)''');
PREPARE stmt_content_type FROM @ddl_content_type; EXECUTE stmt_content_type; DEALLOCATE PREPARE stmt_content_type;

SET @has_idx_article_id = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND INDEX_NAME='idx_article_id');
SET @ddl_drop_idx_article_id = IF(@has_idx_article_id>0, 'ALTER TABLE `comment` DROP INDEX `idx_article_id`', 'SELECT ''legacy idx_article_id absent''');
PREPARE stmt_drop_idx_article_id FROM @ddl_drop_idx_article_id; EXECUTE stmt_drop_idx_article_id; DEALLOCATE PREPARE stmt_drop_idx_article_id;
SET @has_idx_user_id = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND INDEX_NAME='idx_user_id');
SET @ddl_drop_idx_user_id = IF(@has_idx_user_id>0, 'ALTER TABLE `comment` DROP INDEX `idx_user_id`', 'SELECT ''legacy idx_user_id absent''');
PREPARE stmt_drop_idx_user_id FROM @ddl_drop_idx_user_id; EXECUTE stmt_drop_idx_user_id; DEALLOCATE PREPARE stmt_drop_idx_user_id;
SET @has_idx_parent_id = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND INDEX_NAME='idx_parent_id');
SET @ddl_drop_idx_parent_id = IF(@has_idx_parent_id>0, 'ALTER TABLE `comment` DROP INDEX `idx_parent_id`', 'SELECT ''legacy idx_parent_id absent''');
PREPARE stmt_drop_idx_parent_id FROM @ddl_drop_idx_parent_id; EXECUTE stmt_drop_idx_parent_id; DEALLOCATE PREPARE stmt_drop_idx_parent_id;

SET @has_idx_article_time = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND INDEX_NAME='idx_article_time');
SET @ddl_idx_article_time = IF(@has_idx_article_time=0, 'ALTER TABLE `comment` ADD INDEX `idx_article_time` (`article_id`, `deleted`, `create_time`)', 'SELECT ''comment.idx_article_time exists''');
PREPARE stmt_idx_article_time FROM @ddl_idx_article_time; EXECUTE stmt_idx_article_time; DEALLOCATE PREPARE stmt_idx_article_time;
SET @has_idx_root = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND INDEX_NAME='idx_root');
SET @ddl_idx_root = IF(@has_idx_root=0, 'ALTER TABLE `comment` ADD INDEX `idx_root` (`root_id`)', 'SELECT ''comment.idx_root exists''');
PREPARE stmt_idx_root FROM @ddl_idx_root; EXECUTE stmt_idx_root; DEALLOCATE PREPARE stmt_idx_root;

SET @has_reply_user_id = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='reply_user_id');
SET @ddl_drop_reply_user_id = IF(@has_reply_user_id>0, 'ALTER TABLE `comment` DROP COLUMN `reply_user_id`', 'SELECT ''legacy reply_user_id absent''');
PREPARE stmt_drop_reply_user_id FROM @ddl_drop_reply_user_id; EXECUTE stmt_drop_reply_user_id; DEALLOCATE PREPARE stmt_drop_reply_user_id;
SET @has_update_time = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='update_time');
SET @ddl_drop_update_time = IF(@has_update_time>0, 'ALTER TABLE `comment` DROP COLUMN `update_time`', 'SELECT ''legacy update_time absent''');
PREPARE stmt_drop_update_time FROM @ddl_drop_update_time; EXECUTE stmt_drop_update_time; DEALLOCATE PREPARE stmt_drop_update_time;
SET @has_status = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='comment' AND COLUMN_NAME='status');
SET @ddl_drop_status = IF(@has_status>0, 'ALTER TABLE `comment` DROP COLUMN `status`', 'SELECT ''legacy status absent''');
PREPARE stmt_drop_status FROM @ddl_drop_status; EXECUTE stmt_drop_status; DEALLOCATE PREPARE stmt_drop_status;

-- Reconcile denormalized values once and on every rerun; each value is derived from its source rows.
UPDATE article a
SET a.comment_count = (
    SELECT COUNT(*) FROM comment c
    WHERE c.article_id = a.id AND c.deleted = 0 AND c.state = 1
);

UPDATE comment c
LEFT JOIN (
    SELECT root_id, COUNT(*) AS reply_total
    FROM comment
    WHERE deleted = 0 AND state = 1 AND root_id IS NOT NULL
    GROUP BY root_id
) replies ON replies.root_id = c.id
SET c.reply_count = COALESCE(replies.reply_total, 0)
WHERE c.root_id IS NULL;

UPDATE comment c
SET c.like_count = (
    SELECT COUNT(*) FROM user_action ua
    WHERE ua.target_id = c.id AND ua.target_type = 2 AND ua.action_type = 1
);

UPDATE topic t
SET t.post_count = (
    SELECT COUNT(*) FROM post_topic pt WHERE pt.topic_id = t.id
);

UPDATE article a
SET a.cover_img = (
    SELECT pm.url FROM post_media pm
    WHERE pm.article_id = a.id ORDER BY pm.sort_order, pm.id LIMIT 1
)
WHERE EXISTS (SELECT 1 FROM post_media pm WHERE pm.article_id = a.id);
