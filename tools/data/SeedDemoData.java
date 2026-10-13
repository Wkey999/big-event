import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 演示种子数据生成器（单文件，用本地 JDBC 驱动直接执行，无需 Maven/Python 驱动）。
 *
 * 用法（在仓库根目录）：
 *   java -cp D:\develop\repository\com\mysql\mysql-connector-j\9.1.0\mysql-connector-j-9.1.0.jar \
 *        tools/data/SeedDemoData.java [--reset] [--users=30] [--articles=100] [--behaviors=1000]
 *
 * 设计要点：
 *  - 只造自己的数据：用户名固定 demo_user_XX / bot_XX，--reset 只按这些用户名清理
 *  - 固定随机种子 → 两次运行结果一致（可复现）
 *  - 生成的标题/正文为"常青型"内容，不编造具体事件、比分、人名（避免假新闻）
 *  - 生成后回写 article 的 view/like/collect 冗余计数，保证与行为日志一致
 *  - 计数口径：like_count = user_action(action_type=1) 行数；collect_count = action_type=2 行数；
 *              view_count = browse_log 中该文章的去重用户数
 */
public class SeedDemoData {

    static final String URL = "jdbc:mysql://127.0.0.1:3306/big_event?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&rewriteBatchedStatements=true";
    static final String DB_USER = "root";
    static final String DB_PWD = "123456";

    /** BCrypt("123456")，从既有 apitesterA 账号复用；演示用途，所有种子用户共用同一密码 */
    static final String SEED_PWD_HASH = "$2a$10$1UfQ8syBSLPxnOJtXJsX0edasVDxXtRxtn9tZdBsPMI/4AIwj2Y3y";
    static final long RANDOM_SEED = 20261013L;
    static final String MARKER = "[演示] ";

    static final String[] CHANNELS = {"AI热点", "体育", "电竞", "科技数码", "财经商业"};
    static final String[] BOT_USERS = {"bot_ai", "bot_sports", "bot_esports", "bot_tech", "bot_finance"};
    static final String[] BOT_NICKS = {"AI 小助手", "体育小助手", "电竞小助手", "数码小助手", "财经小助手"};

    static final Map<String, String[]> TOPICS = new LinkedHashMap<>();
    static final String[] ANGLES = {"三条路线对比", "落地清单", "常见五个坑", "选型要点",
            "现状与趋势", "一次小规模实测", "成本与收益分析", "团队协作视角"};
    static final String[] SURNAMES = {"林", "陈", "赵", "周", "吴", "郑", "孙", "马", "朱", "胡",
            "郭", "何", "高", "罗", "梁", "宋", "唐", "许", "邓", "冯"};
    static final String[] GIVEN = {"小满", "知行", "一鸣", "子墨", "思远", "雨桐", "嘉禾", "若水",
            "文轩", "晨曦", "俊君", "雅涵", "启明", "宇航", "静姝", "明远",
            "佩琪", "睿哲", "书瑶", "天佑"};

    static {
        TOPICS.put("AI热点", new String[]{"大模型推理成本", "Agent 工具调用", "检索增强生成（RAG）", "开源模型生态",
                "多模态内容理解", "向量检索选型", "提示词工程实践", "模型评测与榜单", "端侧推理部署", "训练数据合规"});
        TOPICS.put("体育", new String[]{"联赛赛程与轮换", "转会窗口的取舍", "青训体系搭建", "运动损伤预防",
                "数据分析在战术中的应用", "体能训练周期化", "主场氛围与心理因素", "业余爱好者的训练计划",
                "裁判判罚与规则演进", "体育内容的传播方式"});
        TOPICS.put("电竞", new String[]{"版本更新后的打法变化", "选手转会与阵容磨合", "战队青训与梯队",
                "赛事观赛体验优化", "电竞外设与手感调校", "复盘方法论", "直播与内容生态", "赛事数据统计口径",
                "业余战队的训练安排", "电竞选手的职业规划"});
        TOPICS.put("科技数码", new String[]{"手机影像的算法取向", "折叠屏铰链与耐久", "芯片能效比", "快充与电池寿命",
                "平板的生产力边界", "智能穿戴的健康数据", "无线耳机延迟", "笔记本散热设计",
                "系统更新与老机型支持", "数码产品的可持续使用"});
        TOPICS.put("财经商业", new String[]{"消费品牌的渠道策略", "供应链韧性", "财报里的三个关键指标", "新茶饮的定价逻辑",
                "跨境电商的合规成本", "会员体系的复购设计", "线下门店的效率模型", "内容电商的转化路径",
                "投融资节奏", "中小企业的现金流管理"});
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        boolean reset = Arrays.asList(args).contains("--reset");
        int userCount = intArg(args, "--users=", 30);
        int articleCount = intArg(args, "--articles=", 100);
        int behaviorCount = intArg(args, "--behaviors=", 1000);

        Random rnd = new Random(RANDOM_SEED);
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PWD)) {
            conn.setAutoCommit(false);
            System.out.println("=== 演示种子数据生成器 ===");
            System.out.printf("  目标：用户 %d（+5 机器作者）、文章 %d、行为 %d%n", userCount, articleCount, behaviorCount);

            Map<String, Long> channelIds = loadChannels(conn);
            System.out.println("  频道：" + channelIds);

            int[] before = counts(conn);
            System.out.printf("  执行前：用户 %d / 文章 %d / browse_log %d / user_action %d%n",
                    before[0], before[1], before[2], before[3]);

            if (reset) {
                int[] removed = resetSeed(conn);
                System.out.printf("  --reset 清理：用户 %d、文章 %d、浏览 %d、行为 %d%n",
                        removed[0], removed[1], removed[2], removed[3]);
                conn.commit();
            } else {
                long existing = one(conn, "SELECT COUNT(*) FROM user WHERE username LIKE 'demo\\_user\\_%' OR username LIKE 'bot\\_%'");
                if (existing > 0) {
                    System.out.println("  检测到已有 " + existing + " 个种子用户 → 请先执行 --reset（避免用户名唯一键冲突）");
                    return;
                }
            }

            List<Long> seedUserIds = insertUsers(conn, userCount);
            List<Long> botUserIds = insertBots(conn);
            System.out.printf("  已插入用户 %d 个（含机器作者 %d 个）%n", seedUserIds.size() + botUserIds.size(), botUserIds.size());

            String coverPool = buildCoverPool(conn);
            insertArticles(conn, rnd, channelIds, seedUserIds, botUserIds, articleCount, coverPool);
            conn.commit();

            List<long[]> artRows = loadSeededArticles(conn, seedUserIds, botUserIds);
            System.out.printf("  已插入文章 %d 篇%n", artRows.size());

            insertBehaviors(conn, rnd, artRows, seedUserIds, behaviorCount);
            conn.commit();

            rewriteCounters(conn, seedUserIds, botUserIds);
            conn.commit();

            int[] after = counts(conn);
            System.out.printf("  执行后：用户 %d / 文章 %d / browse_log %d / user_action %d%n",
                    after[0], after[1], after[2], after[3]);
            System.out.printf("  增量：用户 +%d、文章 +%d、浏览 +%d、行为 +%d%n",
                    after[0] - before[0], after[1] - before[1], after[2] - before[2], after[3] - before[3]);
            reportConsistency(conn, seedUserIds, botUserIds);
        }
    }

    // ---------------- 频道 / 计数 ----------------

    static Map<String, Long> loadChannels(Connection conn) throws SQLException {
        Map<String, Long> map = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, category_name FROM category WHERE deleted = 0")) {
            while (rs.next()) map.put(rs.getString(2), rs.getLong(1));
        }
        for (String c : CHANNELS) {
            if (!map.containsKey(c)) throw new IllegalStateException("缺少频道：" + c + "（现有：" + map.keySet() + "）");
        }
        return map;
    }

    static int[] counts(Connection conn) throws SQLException {
        return new int[]{
                (int) one(conn, "SELECT COUNT(*) FROM user WHERE deleted = 0"),
                (int) one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0"),
                (int) one(conn, "SELECT COUNT(*) FROM browse_log"),
                (int) one(conn, "SELECT COUNT(*) FROM user_action")
        };
    }

    static long one(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    // ---------------- 清理 ----------------

    static int[] resetSeed(Connection conn) throws SQLException {
        // 先取出种子用户 id（MySQL 不允许 DELETE user 时子查询再查 user），再用显式 id 列表清理
        List<Long> ids = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM user WHERE username LIKE 'demo\\_user\\_%' OR username LIKE 'bot\\_%'")) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        if (ids.isEmpty()) return new int[]{0, 0, 0, 0};
        String idList = join(ids);
        int logs = exec(conn, "DELETE FROM browse_log WHERE user_id IN (" + idList + ")");
        int acts = exec(conn, "DELETE FROM user_action WHERE user_id IN (" + idList + ")");
        int arts = exec(conn, "DELETE FROM article WHERE user_id IN (" + idList + ")");
        int users = exec(conn, "DELETE FROM user WHERE id IN (" + idList + ")");
        return new int[]{users, arts, logs, acts};
    }

    static int exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    // ---------------- 用户 ----------------

    static List<Long> insertUsers(Connection conn, int count) throws SQLException {
        String sql = "INSERT INTO user (username, password, nickname, email, avatar, role, user_type, status, login_enabled, create_time, update_time, deleted) "
                + "VALUES (?,?,?,?,'',0,0,1,1,NOW() - INTERVAL ? DAY, NOW(), 0)";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Random r = new Random(RANDOM_SEED + 1);
            for (int i = 1; i <= count; i++) {
                String uname = String.format("demo_user_%02d", i);
                String nick = SURNAMES[r.nextInt(SURNAMES.length)] + GIVEN[r.nextInt(GIVEN.length)];
                ps.setString(1, uname);
                ps.setString(2, SEED_PWD_HASH);
                ps.setString(3, nick);
                ps.setString(4, uname + "@example.com");
                ps.setInt(5, 30 + r.nextInt(120));
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    static List<Long> insertBots(Connection conn) throws SQLException {
        String sql = "INSERT INTO user (username, password, nickname, email, avatar, role, user_type, status, login_enabled, create_time, update_time, deleted) "
                + "VALUES (?,? ,?,?,'',0,1,1,0,NOW() - INTERVAL 90 DAY, NOW(), 0)";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < BOT_USERS.length; i++) {
                ps.setString(1, BOT_USERS[i]);
                ps.setString(2, SEED_PWD_HASH);
                ps.setString(3, BOT_NICKS[i]);
                ps.setString(4, BOT_USERS[i] + "@example.com");
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    // ---------------- 文章 ----------------

    static String buildCoverPool(Connection conn) throws SQLException {
        List<String> covers = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT cover_img FROM article WHERE deleted = 0 AND cover_img <> '' LIMIT 20")) {
            while (rs.next()) covers.add(rs.getString(1));
        }
        return String.join("\u0001", covers);
    }

    static void insertArticles(Connection conn, Random rnd, Map<String, Long> channels,
                               List<Long> users, List<Long> bots, int total, String coverPool) throws SQLException {
        String[] covers = coverPool.isEmpty() ? new String[]{""} : coverPool.split("\u0001");
        String sql = "INSERT INTO article (title, content, cover_img, summary, user_id, category_id, state, view_count, like_count, collect_count, comment_count, create_time, update_time, deleted) "
                + "VALUES (?,?,?,?,?,?,?,0,0,0,0,?,?,0)";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int drafts = Math.max(1, total / 10);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < total; i++) {
                String channel = CHANNELS[i % CHANNELS.length];
                String[] topics = TOPICS.get(channel);
                String subject = topics[(i / CHANNELS.length) % topics.length];
                String angle = ANGLES[(i + i / CHANNELS.length) % ANGLES.length];
                String title = MARKER + subject + "：" + angle;
                boolean isDraft = i < drafts;
                // 草稿之后的头 6 篇已发布文章安排在最近 24 小时内，方便演示时间窗
                boolean recent = !isDraft && (i - drafts) < 6;
                int minutesAgo = recent ? 20 + (i - drafts) * 90 : 60 * 24 + rnd.nextInt(60 * 24 * 59);
                LocalDateTime created = LocalDateTime.now().minusMinutes(minutesAgo);
                LocalDateTime updated = created.plusMinutes(rnd.nextInt(120));
                long userId = (i % 5 < 3 && !bots.isEmpty())             // 60% 由机器作者发布
                        ? bots.get(i % bots.size())
                        : users.get(i % users.size());
                ps.setString(1, title);
                ps.setString(2, body(subject, angle));
                ps.setString(3, covers[i % covers.length]);
                ps.setString(4, MARKER + "围绕「" + subject + "」的" + angle + "，给出可执行的判断依据与注意事项。");
                ps.setLong(5, userId);
                ps.setLong(6, channels.get(channel));
                ps.setInt(7, isDraft ? 0 : 1);
                ps.setString(8, created.format(fmt));
                ps.setString(9, updated.format(fmt));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    static String body(String subject, String angle) {
        return "<p>「" + subject + "」最近在社区里被反复讨论。这篇笔记把与「" + subject + "」相关的要点整理成"
                + angle + "，方便对照自己的场景。</p>"
                + "<p>第一，先明确目标与边界。" + subject + " 的收益通常来自流程简化，而不是单点替换；"
                + "如果目标不清楚，很容易把技术选型本身当成目标。</p>"
                + "<p>第二，把可测量的指标提前定下来：效果、响应耗时、维护成本，三者往往互相挤压，"
                + "需要明确优先级与验收方式。</p>"
                + "<p>第三，留出回退路径。任何与" + subject + "有关的方案，都要能在效果不达预期时快速回到原方案，"
                + "否则上线风险不可控。</p>"
                + "<p>最后把结论写清楚：适用场景、不适用场景、以及下一步验证计划，"
                + "这样团队里其他人才能复用这份" + angle + "。</p>";
    }

    static List<long[]> loadSeededArticles(Connection conn, List<Long> users, List<Long> bots) throws SQLException {
        List<Long> all = new ArrayList<>(users);
        all.addAll(bots);
        String ids = join(all);
        List<long[]> rows = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, category_id, state, UNIX_TIMESTAMP(create_time) FROM article WHERE deleted = 0 AND user_id IN (" + ids + ")")) {
            while (rs.next()) rows.add(new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)});
        }
        return rows;
    }

    // ---------------- 行为 ----------------

    static void insertBehaviors(Connection conn, Random rnd, List<long[]> articles,
                                List<Long> users, int total) throws SQLException {
        if (articles.isEmpty() || users.isEmpty()) return;
        int browseTarget = (int) Math.round(total * 0.75);
        int actionTarget = total - browseTarget;

        String browseSql = "INSERT INTO browse_log (user_id, article_id, category_id, dwell_ms, mode, create_time) VALUES (?,?,?,?,?,?)";
        String[] modes = {"latest", "latest", "latest", "latest", "latest", "latest", "latest",
                "recommend", "recommend", "search", "ask"};
        Set<String> usedBrowse = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(browseSql)) {
            int inserted = 0, guard = 0;
            while (inserted < browseTarget && guard++ < browseTarget * 5) {
                long[] art = articles.get(rnd.nextInt(articles.size()));
                if (art[2] != 1) continue;                       // 只对已发布文章产生行为
                long uid = users.get(rnd.nextInt(users.size()));
                String mode = modes[rnd.nextInt(modes.length)];
                if (!usedBrowse.add(uid + ":" + art[0] + ":" + mode)) continue;   // 同人同文同来源只记一次
                boolean read = rnd.nextInt(10) < 4;               // 40% 真实停留，60% 仅曝光
                int dwell = read ? 3000 + rnd.nextInt(90000) : 0;
                long ts = art[3] + 60L * (rnd.nextInt(60 * 20));   // 文章发布后 20 天内
                ps.setLong(1, uid);
                ps.setLong(2, art[0]);
                ps.setLong(3, art[1]);
                ps.setInt(4, dwell);
                ps.setString(5, mode);
                ps.setString(6, fmt(ts));
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
        }

        String actionSql = "INSERT INTO user_action (user_id, target_id, target_type, action_type, create_time) VALUES (?,?,1,?,?)";
        Set<String> usedAction = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(actionSql)) {
            int inserted = 0, guard = 0;
            while (inserted < actionTarget && guard++ < actionTarget * 10) {
                long[] art = articles.get(rnd.nextInt(articles.size()));
                if (art[2] != 1) continue;
                long uid = users.get(rnd.nextInt(users.size()));
                int actionType = rnd.nextInt(10) < 7 ? 1 : 2;      // 7 成点赞、3 成收藏
                if (!usedAction.add(uid + ":" + art[0] + ":" + actionType)) continue;  // 命中唯一键
                long ts = art[3] + 60L * (rnd.nextInt(60 * 20));
                ps.setLong(1, uid);
                ps.setLong(2, art[0]);
                ps.setInt(3, actionType);
                ps.setString(4, fmt(ts));
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
        }
    }

    static String fmt(long epochSeconds) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(epochSeconds * 1000));
    }

    // ---------------- 计数回写与核对 ----------------

    static void rewriteCounters(Connection conn, List<Long> users, List<Long> bots) throws SQLException {
        String ids = join(concat(users, bots));
        String where = "a.deleted = 0 AND a.user_id IN (" + ids + ")";
        exec(conn, "UPDATE article a SET " +
                "like_count = (SELECT COUNT(*) FROM user_action ua WHERE ua.target_id = a.id AND ua.target_type = 1 AND ua.action_type = 1), " +
                "collect_count = (SELECT COUNT(*) FROM user_action ua WHERE ua.target_id = a.id AND ua.target_type = 1 AND ua.action_type = 2), " +
                "view_count = (SELECT COUNT(DISTINCT bl.user_id) FROM browse_log bl WHERE bl.article_id = a.id) " +
                "WHERE " + where);
        exec(conn, "UPDATE user SET update_time = NOW() WHERE id IN (" + ids + ")");
    }

    static void reportConsistency(Connection conn, List<Long> users, List<Long> bots) throws SQLException {
        String ids = join(concat(users, bots));
        System.out.println("== 一致性核对（种子文章）");
        String sql = "SELECT COUNT(*) FROM ("
                + " SELECT a.id FROM article a WHERE a.deleted = 0 AND a.user_id IN (" + ids + ") AND ("
                + "   a.like_count <> (SELECT COUNT(*) FROM user_action ua WHERE ua.target_id = a.id AND ua.target_type = 1 AND ua.action_type = 1)"
                + "   OR a.collect_count <> (SELECT COUNT(*) FROM user_action ua WHERE ua.target_id = a.id AND ua.target_type = 1 AND ua.action_type = 2)"
                + "   OR a.view_count <> (SELECT COUNT(DISTINCT bl.user_id) FROM browse_log bl WHERE bl.article_id = a.id)"
                + " )) t";
        long mismatches = one(conn, sql);
        System.out.println("  计数与行为日志不一致的文章数 = " + mismatches + (mismatches == 0 ? "  ✅" : "  ❌"));
        System.out.println("  种子文章数 = " + one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0 AND user_id IN (" + ids + ")")
                + "（其中已发布 " + one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0 AND state = 1 AND user_id IN (" + ids + ")")
                + "、草稿 " + one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0 AND state = 0 AND user_id IN (" + ids + ")") + "）");
        System.out.println("  近 24h 已发布文章 = " + one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0 AND state = 1 AND user_id IN (" + ids + ") AND create_time >= NOW() - INTERVAL 1 DAY"));
        System.out.println("  browse_log 按 mode：" + modeSummary(conn, ids));
        System.out.println("  既有数据保护：用户 " + one(conn, "SELECT COUNT(*) FROM user WHERE deleted = 0 AND username NOT LIKE 'demo\\_user\\_%' AND username NOT LIKE 'bot\\_%'")
                + " 个 / 文章 " + one(conn, "SELECT COUNT(*) FROM article WHERE deleted = 0 AND user_id NOT IN (" + ids + ")") + " 篇");
    }

    static String modeSummary(Connection conn, String ids) throws SQLException {
        List<String> parts = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT mode, COUNT(*) FROM browse_log WHERE user_id IN (" + ids + ") GROUP BY mode ORDER BY mode")) {
            while (rs.next()) parts.add(rs.getString(1) + "=" + rs.getLong(2));
        }
        return String.join(", ", parts);
    }

    // ---------------- 工具 ----------------

    static String join(List<Long> ids) {
        StringJoiner sj = new StringJoiner(",");
        for (Long id : ids) sj.add(String.valueOf(id));
        return sj.length() == 0 ? "0" : sj.toString();
    }

    static List<Long> concat(List<Long> a, List<Long> b) {
        List<Long> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    static int intArg(String[] args, String prefix, int def) {
        for (String a : args) {
            if (a.startsWith(prefix)) return Integer.parseInt(a.substring(prefix.length()));
        }
        return def;
    }
}
