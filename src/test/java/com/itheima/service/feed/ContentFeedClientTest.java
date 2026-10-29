package com.itheima.service.feed;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ContentFeedClientTest {
    @Test void rejectsUntrustedFetchAddressesBeforeAnyNetworkCall() {
        for(String url:java.util.List.of("file:///etc/passwd","http://127.0.0.1/rss","https://10.0.0.1/rss",
                "https://internal.lan/rss","https://github.blog.attacker.example/feed","https://user@github.blog/feed","https://github.blog:8443/feed"))
            assertThatThrownBy(() -> ContentFeedClient.validateFeedUri(url)).isInstanceOf(com.itheima.exception.BusinessException.class).hasMessageContaining("白名单");
        for(var source:ContentFeedSource.values()) {
            assertThat(ContentFeedClient.validateFeedUri(source.feedUrl).getScheme()).isEqualTo("https");
            for(String extra:source.supplementalFeedUrls())
                assertThat(ContentFeedClient.validateFeedUri(extra).getScheme()).isEqualTo("https");
        }
    }
    @Test void honorsHttpGbkCharsetWithoutXmlEncodingDeclaration() throws Exception {
        String rss="<rss version='2.0'><channel><title>科技</title><link>https://sspai.com/</link><description>订阅</description><item><title>中文科技资讯</title><link>https://sspai.com/post/1</link></item></channel></rss>";
        assertThat(client.parse(rss.getBytes(java.nio.charset.Charset.forName("GBK")),"application/rss+xml; charset=GBK").getFirst().getTitle()).isEqualTo("中文科技资讯");
        assertThat(client.parse(rss.getBytes(StandardCharsets.UTF_8),"application/rss+xml").getFirst().getTitle()).isEqualTo("中文科技资讯");
    }
    private final ContentFeedClient client=new ContentFeedClient();
    @Test void githubApiAllowlistRejectsOtherRepositoriesAndEndpoints() {
        for(String url:java.util.List.of("https://api.github.com/repos/attacker/model/releases","https://api.github.com/user",
                "https://api.github.com/repos/QwenLM/qwen-code/issues","https://api.github.com/repos/QwenLM/qwen-code/releases?per_page=5",
                "https://api.github.com/repos/QwenLM/BlenderWorld/releases?per_page=5",
                "https://api.github.com/repos/QwenLM/Qwen3/releases?per_page=100"))
            assertThatThrownBy(() -> ContentFeedClient.validateFeedUri(url)).hasMessageContaining("白名单");
        assertThat(ContentFeedClient.validateFeedUri("https://api.github.com/repos/QwenLM/Qwen3/releases?per_page=5").getHost()).isEqualTo("api.github.com");
    }
    @Test void atomFallbackUsesOnlyTheOfficialLatestStableRedirectTarget() throws Exception {
        String stable="https://github.com/QwenLM/Qwen3/releases/tag/v1.0.0";
        String xml="<feed xmlns='http://www.w3.org/2005/Atom'><title>Releases</title><id>urn:releases</id><updated>2026-10-22T00:00:00Z</updated>"
                +"<entry><id>urn:rc</id><title>v2-rc</title><link href='https://github.com/QwenLM/Qwen3/releases/tag/v2-rc'/><updated>2026-10-22T00:00:00Z</updated></entry>"
                +"<entry><id>urn:stable</id><title>v1.0.0</title><link href='"+stable+"'/><updated>2026-10-20T00:00:00Z</updated><content>Do not copy this body</content></entry></feed>";
        var result=client.githubAtomEntries(ContentFeedSource.QWEN_GITHUB,"QwenLM/Qwen3",xml.getBytes(StandardCharsets.UTF_8),stable);
        assertThat(result).hasSize(1); assertThat(result.getFirst().getTitle()).contains("发布版本：v1.0.0");
        assertThat(result.getFirst().getDescription()).isNull();
        assertThatThrownBy(() -> client.githubAtomEntries(ContentFeedSource.QWEN_GITHUB,"QwenLM/Qwen3",xml.getBytes(StandardCharsets.UTF_8),"https://evil.example/QwenLM/Qwen3/releases/tag/v1.0.0"))
                .hasMessageContaining("白名单");
        assertThatThrownBy(() -> ContentFeedClient.validateFeedUri("https://github.com/attacker/model/releases.atom")).hasMessageContaining("白名单");
        assertThatThrownBy(() -> ContentFeedClient.validateFeedUri("https://github.com/QwenLM/qwen-code/releases.atom")).hasMessageContaining("白名单");
        assertThat(ContentFeedClient.validateFeedUri("https://github.com/QwenLM/Qwen3/releases.atom").getHost()).isEqualTo("github.com");
    }
    @Test void repositoryMetadataAndStableReleasesBecomeChineseCardsWithoutReadingBodies() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var metadata=json.readTree("{\"full_name\":\"QwenLM/Qwen3\",\"created_at\":\"2026-10-20T00:00:00Z\",\"private\":false,\"fork\":false}");
        var releases=json.readTree("[{\"tag_name\":\"v1\",\"html_url\":\"https://github.com/QwenLM/Qwen3/releases/tag/v1\",\"published_at\":\"2026-10-21T00:00:00Z\",\"draft\":false,\"prerelease\":false,\"body\":\"NEVER COPY\"},{\"tag_name\":\"v2-rc\",\"draft\":false,\"prerelease\":true},{\"tag_name\":\"draft\",\"draft\":true,\"prerelease\":false}]");
        var results=client.githubEntries(ContentFeedSource.QWEN_GITHUB,"QwenLM/Qwen3",metadata,releases);
        assertThat(results).hasSize(2); assertThat(results.get(0).getTitle()).contains("千问 新开源项目");
        assertThat(results.get(1).getTitle()).contains("发布版本：v1"); assertThat(results.get(1).getDescription()).isNull();
        assertThatThrownBy(() -> client.githubEntries(ContentFeedSource.QWEN_GITHUB,"QwenLM/qwen-code",metadata,releases)).hasMessageContaining("白名单");
        assertThatThrownBy(() -> client.githubEntries(ContentFeedSource.DEEPSEEK_GITHUB,"QwenLM/Qwen3",metadata,releases)).hasMessageContaining("白名单");
    }
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
