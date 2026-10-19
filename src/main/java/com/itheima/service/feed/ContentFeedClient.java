package com.itheima.service.feed;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
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
    private static final Set<String> FEED_HOSTS=Set.of("www.ruanyifeng.com","github.blog","feeds.bbci.co.uk","esportsinsider.com","www.ithome.com","sspai.com");
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public List<SyndEntry> fetch(ContentFeedSource source) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(validateFeedUri(source.feedUrl)).timeout(Duration.ofSeconds(15))
                .header("User-Agent","BOOM-FeedReader/1.0").header("Accept","application/atom+xml,application/rss+xml,application/xml,text/xml").GET().build();
        var pending=client.sendAsync(request,info -> new LimitedBody());
        HttpResponse<byte[]> response;
        try { response=pending.get(20,TimeUnit.SECONDS); }
        catch(Exception e) { pending.cancel(true); throw e; }
        if(response.statusCode()!=200) throw new IllegalStateException("Feed response rejected");
        return parse(response.body(),response.headers().firstValue("Content-Type").orElse("application/xml"));
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
