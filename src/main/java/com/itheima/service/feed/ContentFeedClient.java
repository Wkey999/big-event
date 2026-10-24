package com.itheima.service.feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.Locale;
import com.itheima.exception.BusinessException;

@Component
public class ContentFeedClient {
    private static final Set<String> FEED_HOSTS=Set.of(
            "www.ruanyifeng.com","github.blog","feeds.bbci.co.uk","esportsinsider.com",
            "www.ithome.com","sspai.com","api.github.com","github.com",
            "www.chinanews.com.cn","news.google.com","www.gcores.com","www.ign.com.cn"
    );
    private final ObjectMapper json=new ObjectMapper();
    private volatile long githubApiRetryAt;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public List<SyndEntry> fetch(ContentFeedSource source) throws Exception {
        if(source.repositorySource()) {
            var result=new ArrayList<SyndEntry>();
            for(String repository:source.repositories()) {
                JsonNode metadata=null;
                if(System.currentTimeMillis()>=githubApiRetryAt) {
                    try {
                        metadata=json.readTree(read("https://api.github.com/repos/"+repository).body());
                        var releases=json.readTree(read("https://api.github.com/repos/"+repository+"/releases?per_page=5").body());
                        result.addAll(githubEntries(source,repository,metadata,releases));
                        continue;
                    } catch(ApiRateLimitException e) {
                        if(metadata!=null) result.addAll(githubEntries(source,repository,metadata,json.createArrayNode()));
                    }
                }
                // Public Atom plus GitHub's latest-release redirect identifies one stable
                // version without guessing from tag names or following arbitrary redirects.
                var latest=read("https://github.com/"+repository+"/releases/latest",true);
                if(latest.statusCode()!=302) continue;
                URI location=URI.create("https://github.com/"+repository+"/releases/latest")
                        .resolve(latest.headers().firstValue("Location").orElse(""));
                if(!location.getRawPath().startsWith("/"+repository+"/releases/tag/")) continue;
                var atom=read("https://github.com/"+repository+"/releases.atom");
                result.addAll(githubAtomEntries(source,repository,atom.body(),location.toString()));
            }
            return result.stream().sorted(java.util.Comparator.comparing(SyndEntry::getPublishedDate).reversed()).toList();
        }
        var urls=new ArrayList<String>();
        urls.add(source.feedUrl);
        urls.addAll(source.supplementalFeedUrls());
        var merged=new LinkedHashMap<String,SyndEntry>();
        Exception firstError=null;
        int succeeded=0;
        for(String url:urls) {
            try {
                var response=read(url);
                var parsed=parse(response.body(),response.headers().firstValue("Content-Type").orElse("application/xml"));
                succeeded++;
                for(SyndEntry item:parsed) {
                    String key=item.getLink()!=null && !item.getLink().isBlank() ? item.getLink().trim() : Objects.toString(item.getTitle(),"");
                    if(!key.isBlank()) merged.putIfAbsent(key,item);
                }
            } catch(Exception e) {
                if(firstError==null) firstError=e;
            }
        }
        if(succeeded==0 && firstError!=null) throw firstError;
        var hot=new ArrayList<SyndEntry>();
        var regular=new ArrayList<SyndEntry>();
        for(SyndEntry entry:merged.values()) {
            String tags=entry.getCategories()==null?"":entry.getCategories().stream().map(c -> Objects.toString(c.getName(),"")).reduce("",(a,b) -> a+" "+b);
            if(source.isHotTopic(entry.getTitle(),tags)) hot.add(entry);
            else regular.add(entry);
        }
        hot.addAll(regular);
        return hot;
    }
    private HttpResponse<byte[]> read(String url) throws Exception {
        return read(url,false);
    }
    private HttpResponse<byte[]> read(String url,boolean latestRedirect) throws Exception {
        URI uri=validateFeedUri(url);
        String accept="api.github.com".equals(uri.getHost())?"application/vnd.github+json":"application/atom+xml,application/rss+xml,application/xml,text/xml";
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent","BOOM-FeedReader/1.0").header("Accept",accept).GET().build();
        var pending=client.sendAsync(request,info -> new LimitedBody());
        HttpResponse<byte[]> response;
        try { response=pending.get(20,TimeUnit.SECONDS); }
        catch(Exception e) { pending.cancel(true); throw e; }
        if(response.statusCode()==403 || response.statusCode()==429) {
            if("api.github.com".equals(uri.getHost())) {
                long now=System.currentTimeMillis(); long retry=now+60_000;
                try { retry=Long.parseLong(response.headers().firstValue("X-RateLimit-Reset").orElse(""))*1000; }
                catch(NumberFormatException ignored) {}
                githubApiRetryAt=Math.max(now+30_000,Math.min(retry,now+3_600_000));
                throw new ApiRateLimitException();
            }
            throw new BusinessException("来源接口限制了请求，请稍后重试");
        }
        if(latestRedirect && response.statusCode()==302 && "github.com".equals(uri.getHost()) && uri.getPath().endsWith("/releases/latest")) return response;
        if(response.statusCode()!=200) throw new IllegalStateException("Feed response rejected");
        return response;
    }
    List<SyndEntry> githubAtomEntries(ContentFeedSource source,String repository,byte[] xml,String latestUrl) throws Exception {
        URI latest=URI.create(latestUrl);
        if(!source.repositories().contains(repository) || !"https".equals(latest.getScheme()) || !"github.com".equals(latest.getHost())
                || latest.getUserInfo()!=null || latest.getPort()!=-1 || latest.getRawQuery()!=null || latest.getFragment()!=null
                || !latest.getRawPath().startsWith("/"+repository+"/releases/tag/"))
            throw new BusinessException("正式版本地址不符合仓库白名单");
        String tag=latest.getPath().substring(("/"+repository+"/releases/tag/").length());
        if(tag.isBlank()) return List.of();
        String brand=source==ContentFeedSource.DEEPSEEK_GITHUB?"DeepSeek":"千问";
        var result=new ArrayList<SyndEntry>();
        for(var raw:parse(xml,"application/atom+xml")) {
            if(!latestUrl.equals(raw.getLink())) continue;
            var date=raw.getPublishedDate()!=null?raw.getPublishedDate():raw.getUpdatedDate();
            if(date==null) continue;
            var entry=new SyndEntryImpl(); entry.setTitle(brand+" / "+repository.split("/")[1]+" 发布版本："+tag);
            entry.setLink(latestUrl); entry.setAuthor(repository.split("/")[0]); entry.setPublishedDate(date); result.add(entry);
        }
        return result;
    }
    private static class ApiRateLimitException extends BusinessException {
        ApiRateLimitException() { super("GitHub API 暂时限流，改读官方版本订阅"); }
    }
    List<SyndEntry> githubEntries(ContentFeedSource source,String repository,JsonNode metadata,JsonNode releases) {
        if(!source.repositories().contains(repository) || !repository.equals(metadata.path("full_name").asText())
                || metadata.path("private").asBoolean() || metadata.path("fork").asBoolean() || !releases.isArray())
            throw new BusinessException("官方仓库信息不符合白名单要求");
        var entries=new ArrayList<SyndEntry>();
        String brand=source==ContentFeedSource.DEEPSEEK_GITHUB?"DeepSeek":"千问";
        String project=repository.substring(repository.indexOf('/')+1);
        addGithubEntry(entries,brand+" 新开源项目："+project,"https://github.com/"+repository,metadata.path("created_at").asText(),repository);
        for(var release:releases) {
            if(!release.path("draft").isBoolean() || !release.path("prerelease").isBoolean()
                    || release.path("draft").asBoolean() || release.path("prerelease").asBoolean()) continue;
            String tag=release.path("tag_name").asText();
            if(tag.isBlank()) continue;
            addGithubEntry(entries,brand+" / "+project+" 发布版本："+tag,release.path("html_url").asText(),release.path("published_at").asText(),repository);
        }
        return entries;
    }
    private void addGithubEntry(List<SyndEntry> entries,String title,String url,String date,String repository) {
        try {
            var entry=new SyndEntryImpl(); entry.setTitle(title); entry.setLink(url); entry.setAuthor(repository.split("/")[0]);
            entry.setPublishedDate(java.util.Date.from(java.time.Instant.parse(date))); entries.add(entry);
        } catch(java.time.format.DateTimeParseException e) { /* Missing timestamps never become current news. */ }
    }
    public List<SyndEntry> parse(byte[] xml) throws Exception {
        return parse(xml,"application/xml");
    }
    public List<SyndEntry> parse(byte[] xml,String contentType) throws Exception {
        if(xml.length>2*1024*1024) throw new IllegalArgumentException("Feed too large");
        var input=new SyndFeedInput();
        input.setAllowDoctypes(false);
        // HTTP Content-Type carries the charset. ROME also honors BOM/XML declarations;
        // UTF-8 is the explicit default if all encoding metadata is absent.
        try(var reader=new XmlReader(new ByteArrayInputStream(xml),contentType,true,"UTF-8")) {
            return input.build(reader).getEntries().stream().limit(200).toList();
        }
    }
    static URI validateFeedUri(String value) {
        try {
            URI uri=URI.create(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null
                    || !FEED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) || uri.getUserInfo()!=null
                    || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null)
                throw new IllegalArgumentException();
            if("api.github.com".equalsIgnoreCase(uri.getHost()) && (!ContentFeedSource.trustedGithubApiPath(uri.getRawPath())
                    || (uri.getRawQuery()!=null && !"per_page=5".equals(uri.getRawQuery())))) throw new IllegalArgumentException();
            if("github.com".equalsIgnoreCase(uri.getHost()) && (!ContentFeedSource.trustedGithubFeedPath(uri.getRawPath())
                    || uri.getRawQuery()!=null)) throw new IllegalArgumentException();
            return uri;
        } catch(Exception e) { throw new BusinessException("订阅源地址不受信任，仅允许白名单中的官方 HTTPS 订阅源"); }
    }
    /** Cancel the transfer before accumulating more than 2 MiB, not after reading the whole response. */
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if(buffer.remaining()>2*1024*1024-bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Feed too large")); return;
                }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
