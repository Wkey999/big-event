-- 运行时功能开关：空值表示“跟随配置文件默认值”，显式 true/false 覆盖配置。
-- 幂等：可重复执行；已存在的设置不会被重置。
CREATE TABLE IF NOT EXISTS `app_setting` (
  `setting_key`   varchar(64)  NOT NULL COMMENT '开关键，如 document-assistant.enabled',
  `setting_value` varchar(255) NOT NULL DEFAULT '' COMMENT '空=跟随配置；true/false=显式覆盖',
  `updated_by`    bigint unsigned DEFAULT NULL COMMENT '最后修改的管理员 id',
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行时功能开关';

-- 种一行空值，便于后台直接 UPDATE；空值语义即“跟随配置”
INSERT INTO `app_setting` (`setting_key`, `setting_value`)
VALUES ('document-assistant.enabled', '')
ON DUPLICATE KEY UPDATE `setting_key` = `setting_key`;
