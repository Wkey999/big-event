-- MySQL 5.7. Keep channel IDs, existing articles and interest weights.
START TRANSACTION;
SET @rename_game_channel = (
  (SELECT COUNT(*) FROM category WHERE deleted=0 AND category_name='电竞')=1
  AND (SELECT COUNT(*) FROM category WHERE deleted=0 AND category_name='游戏/电竞')=0
);
UPDATE category SET category_name='游戏/电竞',update_time=NOW()
WHERE @rename_game_channel=1 AND deleted=0 AND category_name='电竞';
UPDATE user_profile_interest i JOIN category c ON c.id=i.category_id
SET i.category_name=c.category_name
WHERE c.deleted=0 AND c.category_name='游戏/电竞' AND i.category_name='电竞';
UPDATE user SET nickname='游戏资讯助手',update_time=NOW()
WHERE username='rss_esports_insider' AND user_type=1 AND role=0 AND login_enabled=0 AND nickname='电竞资讯助手';
COMMIT;
