-- ============================================================
-- Big Event 数据库初始化脚本
-- 数据库: big_event | MySQL 5.7+ | utf8mb4
-- 设计原则: 软删除、无物理外键、联合索引、可扩展
-- ============================================================

CREATE DATABASE IF NOT EXISTS `big_event` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `big_event`;

-- ============================================================
-- 1. 用户表
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`    varchar(20)     NOT NULL COMMENT '用户名（登录账号）',
  `password`    varchar(100)    NOT NULL COMMENT '密码（BCrypt加密）',
  `nickname`    varchar(30)     NOT NULL DEFAULT '' COMMENT '昵称',
  `email`       varchar(100)    NOT NULL DEFAULT '' COMMENT '邮箱',
  `avatar`      varchar(255)    NOT NULL DEFAULT '' COMMENT '头像URL',
  `role`        tinyint         NOT NULL DEFAULT 0 COMMENT '角色：0-普通用户 1-管理员',
  `status`      tinyint         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 文章分类表
-- ============================================================
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_name` varchar(30)     NOT NULL COMMENT '分类名称',
  `user_id`       bigint unsigned NOT NULL COMMENT '所属用户ID',
  `sort_order`    int             NOT NULL DEFAULT 0 COMMENT '排序权重（越小越前）',
  `status`        tinyint         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  UNIQUE INDEX `uk_user_category` (`user_id`, `category_name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章分类表';

-- ============================================================
-- 3. 文章表
-- ============================================================
DROP TABLE IF EXISTS `article`;
CREATE TABLE `article` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title`       varchar(100)    NOT NULL COMMENT '文章标题',
  `content`     text            NOT NULL COMMENT '文章内容（富文本HTML）',
  `cover_img`   varchar(255)    NOT NULL DEFAULT '' COMMENT '封面图URL',
  `summary`     varchar(500)    NOT NULL DEFAULT '' COMMENT '文章摘要（列表展示用）',
  `user_id`     bigint unsigned NOT NULL COMMENT '作者ID',
  `category_id` bigint unsigned          DEFAULT NULL COMMENT '分类ID',
  `state`       tinyint         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布',
  `view_count`  int unsigned    NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count`  int unsigned    NOT NULL DEFAULT 0 COMMENT '点赞次数（冗余计数，避免COUNT）',
  `comment_count` int unsigned  NOT NULL DEFAULT 0 COMMENT '评论次数（冗余计数）',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  INDEX `idx_user_state` (`user_id`, `state`),
  INDEX `idx_category_id` (`category_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_state_create_time` (`state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- ============================================================
-- 4. 标签表
-- ============================================================
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tag_name`    varchar(20)     NOT NULL COMMENT '标签名称',
  `user_id`     bigint unsigned NOT NULL COMMENT '所属用户ID',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_tag` (`user_id`, `tag_name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ============================================================
-- 5. 文章-标签关联表（多对多）
-- ============================================================
DROP TABLE IF EXISTS `article_tag`;
CREATE TABLE `article_tag` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id`  bigint unsigned NOT NULL COMMENT '文章ID',
  `tag_id`      bigint unsigned NOT NULL COMMENT '标签ID',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_article_tag` (`article_id`, `tag_id`),
  INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签关联表';

-- ============================================================
-- 6. 评论表（支持嵌套回复）
-- ============================================================
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id`  bigint unsigned NOT NULL COMMENT '文章ID',
  `user_id`     bigint unsigned NOT NULL COMMENT '评论人ID',
  `parent_id`   bigint unsigned NOT NULL DEFAULT 0 COMMENT '父评论ID（0=顶级评论）',
  `reply_user_id` bigint unsigned        DEFAULT NULL COMMENT '被回复人ID',
  `content`     varchar(1000)   NOT NULL COMMENT '评论内容',
  `like_count`  int unsigned    NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status`      tinyint         NOT NULL DEFAULT 1 COMMENT '状态：0-已屏蔽 1-正常',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  INDEX `idx_article_id` (`article_id`, `create_time`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================================
-- 7. 用户行为表（点赞/收藏/关注，可扩展）
-- ============================================================
DROP TABLE IF EXISTS `user_action`;
CREATE TABLE `user_action` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint unsigned NOT NULL COMMENT '用户ID',
  `target_id`   bigint unsigned NOT NULL COMMENT '目标ID（文章ID/评论ID/用户ID）',
  `target_type` tinyint         NOT NULL COMMENT '目标类型：1-文章 2-评论 3-用户',
  `action_type` tinyint         NOT NULL COMMENT '行为类型：1-点赞 2-收藏 3-关注',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_target_action` (`user_id`, `target_id`, `target_type`, `action_type`),
  INDEX `idx_target` (`target_id`, `target_type`, `action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为表';

-- ============================================================
-- 8. 文件记录表（统一管理上传文件）
-- ============================================================
DROP TABLE IF EXISTS `file_record`;
CREATE TABLE `file_record` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`      bigint unsigned NOT NULL COMMENT '上传者ID',
  `original_name` varchar(255)   NOT NULL COMMENT '原始文件名',
  `file_path`    varchar(500)    NOT NULL COMMENT '存储路径/URL',
  `file_size`    bigint unsigned NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type`    varchar(50)     NOT NULL DEFAULT '' COMMENT '文件MIME类型',
  `usage_type`   tinyint         NOT NULL DEFAULT 0 COMMENT '用途：0-其他 1-头像 2-文章封面 3-文章内容图',
  `create_time`  datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`      tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删 1-已删',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_usage_type` (`usage_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件记录表';
