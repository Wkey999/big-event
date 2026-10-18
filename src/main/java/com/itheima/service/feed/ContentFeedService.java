package com.itheima.service.feed;
import com.itheima.config.ContentFeedProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.ContentFeedMapper;
import com.itheima.pojo.*;
import com.rometools.rome.feed.synd.SyndEntry;
import lombok.RequiredArgsConstructor;
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
public class ContentFeedService {
    private final ContentFeedMapper mapper;
    private final ContentFeedClient client;
    private final ContentFeedProperties properties;
    private final FeedTranslationService translations;
    private final ReentrantLock collecting=new ReentrantLock();
    public List<Map<String,Object>> sources() {
        return Arrays.stream(ContentFeedSource.values()).map(source -> {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("key",source.key); item.put("name",source.name); item.put("feedUrl",source.feedUrl);
            item.put("botNickname",source.botNickname); item.put("automaticEnabled",properties.isEnabled());
            item.put("categoryName",source.categoryName); item.put("description",source.description);
            item.put("requiresTranslation",source.requiresTranslation);
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
            if(cap==0) { run.setSuccess(true); run.setMessage(remaining==0?"今日采集总量已达上限":"该来源今日采集数量已达上限"); return run; }
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
                try { mapper.insertEntry(entry); run.setInserted(run.getInserted()+1); }
                catch(DuplicateKeyException e) { run.setDuplicates(run.getDuplicates()+1); }
            }
            if(source.requiresTranslation) translations.translatePending(source.key);
            run.setSuccess(true); run.setMessage("采集完成，新内容进入待审核列表");
        } catch(Exception e) {
            run.setSuccess(false); run.setMessage("来源读取或入库失败，请检查网络与迁移状态后重试");
        } finally {
            try { run.setFinishedAt(LocalDateTime.now()); mapper.insertRun(run); }
            finally { collecting.unlock(); }
        }
        return run;
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
            if(path==null || !path.startsWith(source.articlePathPrefix()) || path.equals("/")) return null;
            String normalized="https://"+canonicalHost+path; if(normalized.length()>1000) return null;
            FeedEntry entry=new FeedEntry(); entry.setSourceKey(source.key); entry.setSourceName(source.name); entry.setTitle(title);
            entry.setSourceUrl(normalized); entry.setUrlHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))));
            entry.setSourceAuthor(plain(raw.getAuthor(),200));
            if(entry.getSourceAuthor().isBlank() && source==ContentFeedSource.RUANYIFENG) entry.setSourceAuthor("阮一峰");
            if(raw.getPublishedDate()!=null) entry.setSourcePublishedAt(LocalDateTime.ofInstant(raw.getPublishedDate().toInstant(),ZoneId.of("Asia/Shanghai")));
            entry.setFetchedAt(LocalDateTime.now()); entry.setStatus(0); return entry;
        } catch(Exception e) { return null; }
    }
    private static String plain(String value,int limit) {
        String clean=HtmlUtils.htmlUnescape(Objects.toString(value,"")).replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();
        return clean.substring(0,Math.min(limit,clean.length()));
    }
}
