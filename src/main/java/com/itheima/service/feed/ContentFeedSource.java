package com.itheima.service.feed;

import com.itheima.exception.BusinessException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Fixed endpoints only; HTTP callers cannot supply arbitrary URLs. */
public enum ContentFeedSource {
    RUANYIFENG("ruanyifeng", "阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "www.ruanyifeng.com", "科技阅读助手", "科技数码", "科技周刊、工具与项目发现", false),
    GITHUB("github", "GitHub 官方博客", "https://github.blog/feed/", "github.blog", "开源资讯助手", "AI/技术热点", "AI、开发实践与开源内容", true),
    BBC_SPORT("bbc_sport", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "www.bbc.co.uk", "体育资讯助手", "体育", "综合体育、赛事动态与人物报道", true),
    ESPORTS_INSIDER("esports_insider", "Esports Insider", "https://esportsinsider.com/feed", "esportsinsider.com", "电竞资讯助手", "电竞", "电竞赛事、战队与行业动态", true),
    ITHOME("ithome", "IT之家", "https://www.ithome.com/rss/", "www.ithome.com", "数码资讯助手", "科技数码", "数码硬件、软件与科技新闻", false),
    SSPAI("sspai", "少数派", "https://sspai.com/feed", "sspai.com", "工具体验助手", "科技数码", "数字生活、工具体验与使用实践", false);
    private static final Pattern TOPIC = Pattern.compile("(?i)\\b(ai|llms?|agents?|open.source|developers?|engineering|programming|security|coding|copilot|code|models?|software|automation|repository|repositories)\\b");
    private static final Pattern MARKETING = Pattern.compile("(?i)\\b(discount|pricing|sale|register|webinar|sponsor|early.bird)\\b|github (universe|galaxy)|save \\d");
    private static final Pattern SHOPPING = Pattern.compile("京东|天猫|淘宝|拼多多|优惠券|满减|到手价|券后|低至|百亿补贴|[→↓]");
    private static final Pattern BETTING = Pattern.compile("(?i)\\b(betting|casino|gambling|sportsbook|odds|promo.code|gamstop)\\b");
    public final String key, name, feedUrl, articleHost, botNickname, categoryName, description;
    public final boolean requiresTranslation;
    ContentFeedSource(String key, String name, String feedUrl, String articleHost, String botNickname,
                      String categoryName, String description, boolean requiresTranslation) {
        this.key=key; this.name=name; this.feedUrl=feedUrl; this.articleHost=articleHost; this.botNickname=botNickname;
        this.categoryName=categoryName; this.description=description; this.requiresTranslation=requiresTranslation;
    }
    public boolean accepts(String title, String categories) {
        if(this==GITHUB) return !MARKETING.matcher(title).find() && TOPIC.matcher(title+" "+categories).find();
        if(this==ITHOME || this==SSPAI) return !SHOPPING.matcher(title).find();
        if(this==BBC_SPORT || this==ESPORTS_INSIDER) return !BETTING.matcher(title+" "+categories).find();
        return true;
    }
    public String canonicalHost(String host) {
        if(host==null) return null;
        if(articleHost.equalsIgnoreCase(host)) return articleHost;
        if(this==RUANYIFENG && "ruanyifeng.com".equalsIgnoreCase(host)) return articleHost;
        if(this==BBC_SPORT && "www.bbc.com".equalsIgnoreCase(host)) return "www.bbc.com";
        return null;
    }
    public String articlePathPrefix() {
        return this==RUANYIFENG?"/blog/":this==BBC_SPORT?"/sport/":"/";
    }
    public static ContentFeedSource from(String key) {
        return Arrays.stream(values()).filter(source -> source.key.equals(key)).findFirst().orElseThrow(() -> new BusinessException("未知的资讯来源"));
    }
}
