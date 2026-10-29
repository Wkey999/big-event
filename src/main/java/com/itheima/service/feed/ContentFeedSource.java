package com.itheima.service.feed;

import com.itheima.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Fixed endpoints only; HTTP callers cannot supply arbitrary URLs. */
public enum ContentFeedSource {
    RUANYIFENG("ruanyifeng", "阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "www.ruanyifeng.com", "科技阅读助手", "科技数码", "科技周刊、工具与项目发现", false),
    GITHUB("github", "GitHub 官方博客", "https://github.blog/feed/", "github.blog", "开源资讯助手", "AI/技术热点", "AI、开发实践与开源内容", true),
    BBC_SPORT("bbc_sport", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "www.bbc.co.uk", "体育资讯助手", "体育", "综合体育、赛事动态与人物报道", true),
    CHINANEWS_SPORT("chinanews_sport", "中新体育·赛事焦点", "https://www.chinanews.com.cn/rss/sports.xml", "www.chinanews.com.cn", "中文体育助手", "体育", "中超、国乒、NBA、欧冠与国内外焦点体育战报", false),
    ESPORTS_INSIDER("esports_insider", "Esports Insider", "https://esportsinsider.com/feed", "esportsinsider.com", "游戏资讯助手", "游戏/电竞", "全球电竞赛事、S赛/VCT/CS2 赛况与战队动态", true),
    ESPORTS_HOT("esports_hot", "LPL/电竞实时战报", "https://news.google.com/rss/search?q=LPL+OR+%E8%8B%B1%E9%9B%84%E8%81%94%E7%9B%9F+OR+S16+OR+%E6%97%A0%E7%95%8F%E5%A5%91%E7%BA%A6+when:3d&hl=zh-CN&gl=CN&ceid=CN:zh-Hans", "news.google.com", "电竞前线助手", "游戏/电竞", "LPL、S16全球总决赛、无畏契约与机核/IGN实时焦点聚合", false),
    ITHOME("ithome", "IT之家", "https://www.ithome.com/rss/", "www.ithome.com", "数码资讯助手", "科技数码", "数码硬件、软件与科技新闻", false),
    SSPAI("sspai", "少数派", "https://sspai.com/feed", "sspai.com", "工具体验助手", "科技数码", "数字生活、工具体验与使用实践", false),
    DEEPSEEK_GITHUB("deepseek_github", "DeepSeek 官方仓库", "https://api.github.com/repos/deepseek-ai/DeepInfer", "github.com", "DeepSeek 动态助手", "AI/技术热点", "官方白名单项目公开与正式版本发布", false),
    QWEN_GITHUB("qwen_github", "千问官方仓库", "https://api.github.com/repos/QwenLM/Qwen3", "github.com", "千问动态助手", "AI/技术热点", "仅 Qwen3 核心仓库动态，采集后由管理员审核", false);

    private static final Pattern TOPIC = Pattern.compile("(?i)\\b(ai|llms?|agents?|open.source|developers?|engineering|programming|security|coding|copilot|code|models?|software|automation|repository|repositories)\\b");
    private static final Pattern MARKETING = Pattern.compile("(?i)\\b(discount|pricing|sale|register|webinar|sponsor|early.bird)\\b|github (universe|galaxy)|save \\d");
    private static final Pattern SHOPPING = Pattern.compile("京东|天猫|淘宝|拼多多|优惠券|满减|到手价|券后|低至|百亿补贴|[→↓]");
    private static final Pattern BETTING = Pattern.compile("(?i)\\b(betting|casino|gambling|sportsbook|odds|promo.code|gamstop|kalshi)\\b");
    private static final Pattern HOT_PULSE = Pattern.compile("(?i)(?<![a-z0-9])(?:LPL|LCK|S16|Worlds|BLG|TES|WBG|IG|JDG|T1|GEN|Faker|Bin|Rookie|VALORANT|VCT|CS2|Dota|NBA|DeepSeek|Qwen|GPT|Claude|Agent)(?![a-z0-9])|全球总决赛|英雄联盟|零封|横扫|爆冷|夺冠|全败|淘汰|瑞士轮|无畏契约|国乒|中超|欧冠|英超|决赛|金牌|千问|大模型|开源");

    public final String key, name, feedUrl, articleHost, botNickname, categoryName, description;
    public final boolean requiresTranslation;

    ContentFeedSource(String key, String name, String feedUrl, String articleHost, String botNickname,
                      String categoryName, String description, boolean requiresTranslation) {
        this.key = key;
        this.name = name;
        this.feedUrl = feedUrl;
        this.articleHost = articleHost;
        this.botNickname = botNickname;
        this.categoryName = categoryName;
        this.description = description;
        this.requiresTranslation = requiresTranslation;
    }

    public List<String> supplementalFeedUrls() {
        return switch (this) {
            case ESPORTS_INSIDER -> List.of(
                    "https://esportsinsider.com/?s=LPL&feed=rss2",
                    "https://esportsinsider.com/?s=Worlds+2026&feed=rss2",
                    "https://esportsinsider.com/?s=League+of+Legends&feed=rss2",
                    "https://esportsinsider.com/?s=VALORANT&feed=rss2"
            );
            case ESPORTS_HOT -> List.of(
                    "https://www.gcores.com/rss",
                    "https://www.ign.com.cn/feed.xml"
            );
            case CHINANEWS_SPORT -> List.of(
                    "https://news.google.com/rss/search?q=NBA+OR+%E6%AC%A7%E5%86%A0+OR+%E8%8B%B1%E8%B6%85+OR+%E5%9B%BD%E4%B9%92+OR+%E4%B8%AD%E8%B6%85+when:2d&hl=zh-CN&gl=CN&ceid=CN:zh-Hans"
            );
            case BBC_SPORT -> List.of(
                    "https://feeds.bbci.co.uk/sport/football/rss.xml"
            );
            default -> List.of();
        };
    }

    public boolean isHotTopic(String title, String categories) {
        return HOT_PULSE.matcher(
                (title == null ? "" : title) + " " + (categories == null ? "" : categories)
        ).find();
    }

    public boolean accepts(String title, String categories) {
        if (this == GITHUB) return !MARKETING.matcher(title).find() && TOPIC.matcher(title + " " + categories).find();
        if (this == ITHOME || this == SSPAI) return !SHOPPING.matcher(title).find();
        if (this == BBC_SPORT || this == ESPORTS_INSIDER || this == ESPORTS_HOT || this == CHINANEWS_SPORT)
            return !BETTING.matcher(title + " " + categories).find() && !SHOPPING.matcher(title).find();
        return true;
    }

    public String canonicalHost(String host) {
        if (host == null) return null;
        if (articleHost.equalsIgnoreCase(host)) return articleHost;
        if (this == RUANYIFENG && "ruanyifeng.com".equalsIgnoreCase(host)) return articleHost;
        if (this == BBC_SPORT && "www.bbc.com".equalsIgnoreCase(host)) return "www.bbc.com";
        if (this == CHINANEWS_SPORT && ("chinanews.com.cn".equalsIgnoreCase(host) || "www.chinanews.com".equalsIgnoreCase(host)))
            return "www.chinanews.com.cn";
        if (this == CHINANEWS_SPORT && "news.google.com".equalsIgnoreCase(host)) return "news.google.com";
        if (this == ESPORTS_HOT) {
            if ("www.gcores.com".equalsIgnoreCase(host) || "gcores.com".equalsIgnoreCase(host)) return "www.gcores.com";
            if ("www.ign.com.cn".equalsIgnoreCase(host) || "ign.com.cn".equalsIgnoreCase(host)) return "www.ign.com.cn";
        }
        return null;
    }

    public String articlePathPrefix() {
        return this == RUANYIFENG ? "/blog/" : this == BBC_SPORT ? "/sport/" : "/";
    }

    public List<String> repositories() {
        return this == DEEPSEEK_GITHUB ? List.of("deepseek-ai/DeepInfer", "deepseek-ai/deepseek-harness", "deepseek-ai/DeepSeek-R1")
                : this == QWEN_GITHUB ? List.of("QwenLM/Qwen3") : List.of();
    }

    public boolean repositorySource() { return !repositories().isEmpty(); }
    // Qwen remains collectable, but its release cards require an administrator's review.
    // This hard boundary also rejects stale production configuration that still lists qwen_github.
    public boolean autoPublishEligible() { return this == GITHUB || this == DEEPSEEK_GITHUB; }

    public boolean acceptsArticlePath(String path) {
        if (!path.startsWith(articlePathPrefix())) return false;
        if (!repositorySource()) return true;
        return repositories().stream().anyMatch(repo -> path.equals("/" + repo)
                || (path.startsWith("/" + repo + "/releases/tag/") && path.length() > ("/" + repo + "/releases/tag/").length()));
    }

    static boolean trustedGithubApiPath(String path) {
        return Arrays.stream(values()).flatMap(source -> source.repositories().stream())
                .anyMatch(repo -> path.equals("/repos/" + repo) || path.equals("/repos/" + repo + "/releases"));
    }

    static boolean trustedGithubFeedPath(String path) {
        return Arrays.stream(values()).flatMap(source -> source.repositories().stream())
                .anyMatch(repo -> path.equals("/" + repo + "/releases.atom") || path.equals("/" + repo + "/releases/latest"));
    }

    public static ContentFeedSource from(String key) {
        return Arrays.stream(values()).filter(source -> source.key.equals(key)).findFirst().orElseThrow(() -> new BusinessException("未知的资讯来源"));
    }
}
