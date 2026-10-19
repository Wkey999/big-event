package com.itheima.service.feed;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ContentFeedClientTest {
    @Test void rejectsUntrustedFetchAddressesBeforeAnyNetworkCall() {
        for(String url:java.util.List.of("file:///etc/passwd","http://127.0.0.1/rss","https://10.0.0.1/rss",
                "https://internal.lan/rss","https://github.blog.attacker.example/feed","https://user@github.blog/feed","https://github.blog:8443/feed"))
            assertThatThrownBy(() -> ContentFeedClient.validateFeedUri(url)).isInstanceOf(com.itheima.exception.BusinessException.class).hasMessageContaining("白名单");
        for(var source:ContentFeedSource.values()) assertThat(ContentFeedClient.validateFeedUri(source.feedUrl).getScheme()).isEqualTo("https");
    }
    @Test void honorsHttpGbkCharsetWithoutXmlEncodingDeclaration() throws Exception {
        String rss="<rss version='2.0'><channel><title>科技</title><link>https://sspai.com/</link><description>订阅</description><item><title>中文科技资讯</title><link>https://sspai.com/post/1</link></item></channel></rss>";
        assertThat(client.parse(rss.getBytes(java.nio.charset.Charset.forName("GBK")),"application/rss+xml; charset=GBK").getFirst().getTitle()).isEqualTo("中文科技资讯");
        assertThat(client.parse(rss.getBytes(StandardCharsets.UTF_8),"application/rss+xml").getFirst().getTitle()).isEqualTo("中文科技资讯");
    }
    private final ContentFeedClient client=new ContentFeedClient();
    @Test void readsRssAndAtomWithoutUsingDescriptionsAsArticles() throws Exception {
        var rss=client.parse("<rss version=\"2.0\"><channel><title>Example</title><link>https://github.blog/</link><description>Feed</description><item><title>AI</title><link>https://github.blog/example/</link><description>DO NOT PERSIST THIS BODY</description></item></channel></rss>".getBytes(StandardCharsets.UTF_8));
        assertThat(rss).hasSize(1);
        assertThat(ContentFeedService.toEntry(ContentFeedSource.GITHUB,rss.getFirst()).getTitle()).isEqualTo("AI");
        var atom=client.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>Blog</title><id>urn:test</id><updated>2026-10-16T00:00:00Z</updated><entry><title>Week</title><id>urn:entry</id><link href=\"http://www.ruanyifeng.com/blog/example.html\"/><updated>2026-10-16T00:00:00Z</updated></entry></feed>".getBytes(StandardCharsets.UTF_8));
        assertThat(atom).hasSize(1);
    }
    @Test void rejectsOversizedResponsesAndExternalEntities() {
        assertThatThrownBy(() -> client.parse(new byte[2*1024*1024+1])).isInstanceOf(IllegalArgumentException.class);
        String xml="<!DOCTYPE rss [<!ENTITY secret SYSTEM 'file:///should-never-read'>]><rss version='2.0'><channel><title>&secret;</title></channel></rss>";
        assertThatThrownBy(() -> client.parse(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(Exception.class);
    }
}
