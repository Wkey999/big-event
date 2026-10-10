-- MySQL 5.7.26：将频道名称唯一性从「每个 user_id 内」升级为全站唯一。
-- 只约束未软删除的频道；生成列在 deleted=1 时为 NULL，因此可保留多条同名历史记录。
--
-- 这是迁移脚本，不要在已有数据库上重新执行 big_event_init.sql（该脚本会 DROP 表）。
-- 执行前先检查活动频道重名：
SELECT category_name, COUNT(*) AS duplicate_count, GROUP_CONCAT(id ORDER BY id) AS category_ids
FROM category
WHERE deleted = 0
GROUP BY category_name
HAVING COUNT(*) > 1;

-- 若上面的查询返回记录，先逐条确认并手工重命名重复频道（保留关联文章），再执行下面的 ALTER。
-- 不自动软删除或删除重复行，避免让既有文章失去有效频道。
-- 本 ALTER 若因重复数据失败，不会删除或改写频道数据；处理完重名后重新执行即可。
ALTER TABLE category
    DROP INDEX uk_user_category,
    ADD COLUMN active_category_name VARCHAR(30)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN category_name ELSE NULL END) STORED
        COMMENT '仅活动频道参与全站唯一约束' AFTER deleted,
    ADD UNIQUE INDEX uk_category_name (active_category_name);

-- 验收：SHOW INDEX FROM category; 应包含 uk_category_name。
