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
  `active_category_name` varchar(30) GENERATED ALWAYS AS (
    CASE WHEN `deleted` = 0 THEN `category_name` ELSE NULL END
  ) STORED COMMENT '仅活动频道参与全站唯一约束',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  UNIQUE INDEX `uk_category_name` (`active_category_name`)
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
  `collect_count` int unsigned  NOT NULL DEFAULT 0 COMMENT '收藏次数（冗余计数，与 like_count 对称）',
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

-- ============================================================
-- 9. 浏览行为日志表（阶段 A 数据闭环 · 推荐系统的信号源）
-- 说明：category_id 必须允许 NULL —— article.category_id 本身就允许 NULL（未分类文章），
--       这里若写 NOT NULL，埋点会因非空约束失败，而埋点按设计是静默失败的，最难排查。
-- ============================================================
DROP TABLE IF EXISTS `browse_log`;
CREATE TABLE `browse_log` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint unsigned NOT NULL COMMENT '浏览者ID',
  `article_id`  bigint unsigned NOT NULL COMMENT '文章ID',
  `category_id` bigint unsigned          DEFAULT NULL COMMENT '频道ID（冗余，聚合免JOIN；文章可未分类）',
  `dwell_ms`    int             NOT NULL DEFAULT 0 COMMENT '停留毫秒，0=仅曝光',
  `mode`        varchar(16)     NOT NULL DEFAULT 'latest' COMMENT '曝光来源：latest/recommend',
  `create_time` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_time` (`user_id`, `create_time`),
  INDEX `idx_article` (`article_id`),
  INDEX `idx_mode_time` (`mode`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览行为日志（推荐信号来源）';

-- ============================================================
-- 10. 用户偏好画像主表（阶段 B' 规则画像；阶段 B 由 LLM 归纳覆盖同一张表）
-- 设计取舍：权重明细独立成子表而不是 JSON 列 —— 重排要按 categoryId 取权重，
--           子表天然按 (user_id, category_id) 索引，也省掉 MyBatis TypeHandler。
-- ============================================================
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
  `user_id`      bigint unsigned NOT NULL COMMENT '用户ID（一人一行）',
  `active_hours` varchar(64)     NOT NULL DEFAULT '' COMMENT '活跃小时，逗号分隔（降序前3个）',
  `source`       varchar(16)     NOT NULL DEFAULT 'rule' COMMENT '画像来源：rule-规则统计 llm-大模型归纳',
  `sample_size`  int             NOT NULL DEFAULT 0 COMMENT '参与统计的行为条数（置信度参考）',
  `generated_at` datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好画像（推荐重排的输入）';

-- ============================================================
-- 11. 画像-频道权重明细
-- ============================================================
DROP TABLE IF EXISTS `user_profile_interest`;
CREATE TABLE `user_profile_interest` (
  `user_id`       bigint unsigned NOT NULL COMMENT '用户ID',
  `category_id`   bigint unsigned NOT NULL COMMENT '频道ID',
  `category_name` varchar(30)     NOT NULL DEFAULT '' COMMENT '频道名快照（展示用，免JOIN）',
  `weight`        decimal(6,4)    NOT NULL DEFAULT 0 COMMENT '归一化权重（0~1，总和≈1）',
  `pv`            int             NOT NULL DEFAULT 0 COMMENT '近7天浏览量',
  `avg_dwell_ms`  int             NOT NULL DEFAULT 0 COMMENT '平均停留毫秒',
  `actions`       int             NOT NULL DEFAULT 0 COMMENT '点赞+收藏数（高权重信号）',
  PRIMARY KEY (`user_id`, `category_id`),
  INDEX `idx_user_weight` (`user_id`, `weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='画像-频道权重明细';
