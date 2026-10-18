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

@Component
public class ContentFeedClient {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public List<SyndEntry> fetch(ContentFeedSource source) throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create(source.feedUrl)).timeout(Duration.ofSeconds(15))
                .header("User-Agent","BOOM-FeedReader/1.0").header("Accept","application/atom+xml,application/rss+xml,application/xml,text/xml").GET().build();
        var pending=client.sendAsync(request,info -> new LimitedBody());
        HttpResponse<byte[]> response;
        try { response=pending.get(20,TimeUnit.SECONDS); }
        catch(Exception e) { pending.cancel(true); throw e; }
        if(response.statusCode()!=200) throw new IllegalStateException("Feed response rejected");
        return parse(response.body());
    }
    public List<SyndEntry> parse(byte[] xml) throws Exception {
        if(xml.length>2*1024*1024) throw new IllegalArgumentException("Feed too large");
        var input=new SyndFeedInput();
        input.setAllowDoctypes(false);
        try(var reader=new XmlReader(new ByteArrayInputStream(xml))) {
            return input.build(reader).getEntries().stream().limit(200).toList();
        }
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
