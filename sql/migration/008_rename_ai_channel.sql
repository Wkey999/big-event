-- Preserve category IDs and article assignments. Skip if the target name already exists.
START TRANSACTION;
SET @rename_ai_channel = (
  (SELECT COUNT(*) FROM category WHERE deleted=0 AND category_name='AI热点')=1
  AND (SELECT COUNT(*) FROM category WHERE deleted=0 AND category_name='AI/技术热点')=0
);
UPDATE category SET category_name='AI/技术热点',update_time=NOW()
WHERE @rename_ai_channel=1 AND deleted=0 AND category_name='AI热点';
UPDATE user_profile_interest i JOIN category c ON c.id=i.category_id
SET i.category_name=c.category_name
WHERE c.deleted=0 AND c.category_name='AI/技术热点' AND i.category_name='AI热点';
COMMIT;
