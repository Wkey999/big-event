package com.itheima.service.feed;
import com.itheima.config.ContentFeedProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.ContentFeedMapper;
import com.itheima.pojo.*;
import com.rometools.rome.feed.synd.SyndEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentFeedService {
    private final ContentFeedMapper mapper;
    private final ContentFeedClient client;
    private final ContentFeedProperties properties;
    private final FeedTranslationService translations;
    private final FeedPublishPolicy policy;
    private final FeedPublishingService publishing;
    private final ReentrantLock collecting=new ReentrantLock();
    public List<Map<String,Object>> sources() {
        return Arrays.stream(ContentFeedSource.values()).map(source -> {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("key",source.key); item.put("name",source.name); item.put("feedUrl",source.feedUrl);
            item.put("botNickname",source.botNickname); item.put("automaticEnabled",properties.isEnabled());
            item.put("categoryName",source.categoryName); item.put("description",source.description);
            item.put("requiresTranslation",source.requiresTranslation);
            item.put("automaticPublishing",policy.automatic(source));
            item.put("repositories",source.repositories());
            item.put("feedCount",1+source.supplementalFeedUrls().size());
            item.put("hotTopicBoost",!source.repositorySource());
            item.put("lastRun",mapper.latestRun(source.key)); return item;
        }).toList();
    }
    public FeedRun collect(String key) {
        ContentFeedSource source=ContentFeedSource.from(key);
        if(!collecting.tryLock()) throw new BusinessException("已有采集任务正在执行，请稍后再试");
        FeedRun run=new FeedRun(); run.setSourceKey(key); run.setStartedAt(LocalDateTime.now());
        try {
            long remaining=Math.max(0,Math.max(1,Math.min(200,properties.getMaxNewPerDay()))-mapper.countSince(LocalDate.now().atStartOfDay()));
            long sourceRemaining=Math.max(0,Math.max(1,Math.min(100,properties.getMaxNewPerSourcePerDay()))-mapper.countSourceSince(key,LocalDate.now().atStartOfDay()));
            int cap=(int)Math.min(Math.min(remaining,sourceRemaining),Math.max(1,Math.min(20,properties.getMaxNewPerSource())));
            if(cap==0) {
                run.setSkipped(true); run.setSuccess(false); run.setMessage(remaining==0?"今日采集总量已达上限":"该来源今日采集数量已达上限");
                if(policy.automatic(source)) { if(source.requiresTranslation) translations.translatePending(source.key); publishPending(source,run); }
                return run;
            }
            LocalDateTime oldest=LocalDateTime.now().minusDays(Math.max(1,Math.min(90,properties.getMaxAgeDays())));
            for(SyndEntry raw:client.fetch(source)) {
                if(run.getInserted()>=cap) break;
                run.setScanned(run.getScanned()+1);
                FeedEntry entry=toEntry(source,raw);
                String tags=raw.getCategories().stream().map(c -> Objects.toString(c.getName(),"")).reduce("",(a,b) -> a+" "+b);
                if(entry==null || !source.accepts(entry.getTitle(),tags) || (entry.getSourcePublishedAt()!=null
                        && (entry.getSourcePublishedAt().isBefore(oldest) || entry.getSourcePublishedAt().isAfter(LocalDateTime.now().plusDays(1))))) {
                    run.setFiltered(run.getFiltered()+1); continue;
                }
                entry.setAutoPublishRequested(policy.automatic(source)?1:0);
                try { mapper.insertEntry(entry); run.setInserted(run.getInserted()+1); }
                catch(DuplicateKeyException e) { run.setDuplicates(run.getDuplicates()+1); }
            }
            if(source.requiresTranslation) translations.translatePending(source.key);
            if(policy.automatic(source)) publishPending(source,run);
            run.setSuccess(true); run.setMessage(policy.automatic(source)?"采集完成，白名单内容自动发布；未就绪条目保留待处理":"采集完成，新内容进入待审核列表");
        } catch(Exception e) {
            log.warn("资讯采集失败 source={}, type={}", source.key, e.getClass().getSimpleName());
            run.setSuccess(false); run.setMessage(e instanceof BusinessException ? e.getMessage() : "来源读取或入库失败，请检查网络与迁移状态后重试");
        } finally {
            try { run.setFinishedAt(LocalDateTime.now()); mapper.insertRun(run); }
            finally { collecting.unlock(); }
        }
        return run;
    }
    private void publishPending(ContentFeedSource source,FeedRun run) {
        for(Long id:mapper.automaticCandidates(source.key)) {
            try { if(publishing.publishAutomatically(id)) run.setAutoPublished(run.getAutoPublished()+1); }
            catch(Exception e) { log.warn("白名单条目保留待处理 source={}, entry={}, type={}",source.key,id,e.getClass().getSimpleName()); }
        }
        run.setAutoPending(mapper.countAutomaticPending(source.key));
    }
    public PageBean<FeedEntry> entries(Integer page,Integer status,String source) {
        if(status!=null && (status<0 || status>2)) throw new BusinessException("未知的审核状态");
        if(source!=null) ContentFeedSource.from(source);
        int safePage=Math.max(1,Math.min(100000,page==null?1:page));
        return new PageBean<>(mapper.countEntries(status,source),mapper.listEntries(status,source,(safePage-1)*10,10));
    }
    static FeedEntry toEntry(ContentFeedSource source,SyndEntry raw) {
        try {
            String title=plain(raw.getTitle(),500);
            if(title.isBlank() || raw.getLink()==null) return null;
            URI url=URI.create(raw.getLink().trim()); String host=url.getHost();
            if(!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme())) || url.getUserInfo()!=null || url.getPort()!=-1 || host==null) return null;
            String canonicalHost=source.canonicalHost(host); if(canonicalHost==null) return null;
            String path=url.normalize().getRawPath();
            if(path==null || !source.acceptsArticlePath(path) || path.equals("/")) return null;
            String normalized="https://"+canonicalHost+path; if(normalized.length()>1000) return null;
            FeedEntry entry=new FeedEntry(); entry.setSourceKey(source.key); entry.setSourceName(source.name); entry.setTitle(title);
            entry.setSourceUrl(normalized); entry.setUrlHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))));
            entry.setSourceAuthor(plain(raw.getAuthor(),200));
            if(entry.getSourceAuthor().isBlank()) {
                if(source==ContentFeedSource.RUANYIFENG) entry.setSourceAuthor("阮一峰");
                else if("news.google.com".equalsIgnoreCase(canonicalHost)) {
                    int dash=title.lastIndexOf(" - ");
                    if(dash>4 && dash<title.length()-1) entry.setSourceAuthor(plain(title.substring(dash+3),100));
                    else entry.setSourceAuthor("全网赛事快讯");
                } else if(source==ContentFeedSource.CHINANEWS_SPORT) entry.setSourceAuthor("中国新闻网");
                else if("www.gcores.com".equalsIgnoreCase(canonicalHost)) entry.setSourceAuthor("机核 GCORES");
                else if("www.ign.com.cn".equalsIgnoreCase(canonicalHost)) entry.setSourceAuthor("IGN中国");
            }
            if(raw.getPublishedDate()!=null) entry.setSourcePublishedAt(LocalDateTime.ofInstant(raw.getPublishedDate().toInstant(),ZoneId.of("Asia/Shanghai")));
            entry.setFetchedAt(LocalDateTime.now()); entry.setStatus(0); entry.setAutoPublishRequested(0); return entry;
        } catch(Exception e) { return null; }
    }
    private static String plain(String value,int limit) {
        String clean=HtmlUtils.htmlUnescape(Objects.toString(value,"")).replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();
        return clean.substring(0,Math.min(limit,clean.length()));
    }
}
