package com.itheima.service.feed;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ContentFeedClientTest {
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
