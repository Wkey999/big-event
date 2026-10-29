-- Apply after 012_feed_automatic_publish.sql. MySQL 5.7 compatible and repeatable.
-- Pending Qwen entries lose the old automatic flag. Previously auto-published
-- articles become drafts, while manually reviewed articles remain public.
START TRANSACTION;
UPDATE feed_entry
SET auto_publish_requested = 0
WHERE source_key = 'qwen_github'
  AND status = 0
  AND auto_publish_requested = 1;

-- Previously auto-published Qwen release cards go back to article drafts.
-- Preserve feed_entry and article_source for audit; manually reviewed Qwen posts stay public.
UPDATE article AS a
JOIN feed_entry AS f ON f.article_id = a.id
SET a.state = 0
WHERE f.source_key = 'qwen_github'
  AND f.status = 1
  AND f.publish_mode = 1
  AND a.state = 1
  AND a.deleted = 0;
COMMIT;
