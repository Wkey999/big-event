package com.itheima.service.feed;

import com.itheima.config.ContentFeedProperties;
import com.itheima.mapper.ContentFeedMapper;
import com.itheima.pojo.FeedEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContentFeedServiceTest {
    private final ContentFeedMapper mapper=mock(ContentFeedMapper.class);
    private final ContentFeedClient client=mock(ContentFeedClient.class);
    private final ContentFeedProperties properties=new ContentFeedProperties();
    private final FeedTranslationService translations=mock(FeedTranslationService.class);
    private final FeedPublishPolicy policy=new FeedPublishPolicy(properties);
    private final FeedPublishingService publishing=mock(FeedPublishingService.class);
    private final ContentFeedService service=new ContentFeedService(mapper,client,properties,translations,policy,publishing);
    private SyndEntryImpl entry(String url) {
        var entry=new SyndEntryImpl(); entry.setTitle("AI tools &amp; engineering"); entry.setLink(url); return entry;
    }
    @Test void newWhitelistedEntriesAreOptedInTranslatedThenPublished() throws Exception {
        properties.setAutoPublishEnabled(true);
        when(client.fetch(ContentFeedSource.GITHUB)).thenReturn(List.of(entry("https://github.blog/engineering/agents/")));
        when(mapper.automaticCandidates("github")).thenReturn(List.of(12L));
        when(publishing.publishAutomatically(12L)).thenReturn(true);
        var run=service.collect("github");
        assertThat(run.getAutoPublished()).isEqualTo(1);
        verify(mapper).insertEntry(argThat(row -> row.getAutoPublishRequested()==1));
        var order=inOrder(translations,publishing);
        order.verify(translations).translatePending("github"); order.verify(publishing).publishAutomatically(12L);
    }
    @Test void ordinarySourcesStayManualEvenWithAutomaticMasterEnabled() throws Exception {
        properties.setAutoPublishEnabled(true);
        when(client.fetch(ContentFeedSource.SSPAI)).thenReturn(List.of(entry("https://sspai.com/post/123")));
        service.collect("sspai");
        verify(mapper).insertEntry(argThat(row -> row.getAutoPublishRequested()==0));
        verifyNoInteractions(publishing);
    }
    @Test void cappedCollectionStillRetriesPreviouslyOptedInEntries() {
        properties.setAutoPublishEnabled(true);
        when(mapper.countSince(any())).thenReturn(60L);
        when(mapper.automaticCandidates("github")).thenReturn(List.of(12L));
        when(publishing.publishAutomatically(12L)).thenReturn(true);
        var run=service.collect("github");
        assertThat(run.isSkipped()).isTrue(); assertThat(run.getAutoPublished()).isEqualTo(1);
        verifyNoInteractions(client);
    }
    @Test void automaticFailureKeepsCollectionSuccessfulAndEntryPending() throws Exception {
        properties.setAutoPublishEnabled(true);
        when(client.fetch(ContentFeedSource.GITHUB)).thenReturn(List.of());
        when(mapper.automaticCandidates("github")).thenReturn(List.of(12L));
        when(publishing.publishAutomatically(12L)).thenThrow(new com.itheima.exception.BusinessException("中文标题尚未就绪"));
        when(mapper.countAutomaticPending("github")).thenReturn(1);
        var run=service.collect("github");
        assertThat(run.isSuccess()).isTrue(); assertThat(run.getAutoPublished()).isZero(); assertThat(run.getAutoPending()).isEqualTo(1);
    }
    @Test void repositoryArticlesMustBelongToTheExactRepositoryAllowlist() {
        var source=ContentFeedSource.DEEPSEEK_GITHUB;
        for(String path:List.of("/attacker/DeepInfer/releases/tag/v1","/deepseek-ai/DeepInfer-fake/releases/tag/v1","/deepseek-ai/DeepInfer/issues/1","/deepseek-ai/DeepInfer/releases/tag/"))
            assertThat(ContentFeedService.toEntry(source,entry("https://github.com"+path))).isNull();
        assertThat(ContentFeedService.toEntry(source,entry("https://github.com/deepseek-ai/DeepInfer/releases/tag/v1"))).isNotNull();
        assertThat(ContentFeedService.toEntry(source,entry("https://github.com/deepseek-ai/DeepInfer"))).isNotNull();
    }
    @Test void canonicalizesLegacyPermalinksAndNeverCopiesBody() {
        var raw=entry("http://ruanyifeng.com/blog/2026/10/example.html?utm_source=rss#part");
        raw.setAuthor("阮一峰"); raw.setPublishedDate(Date.from(Instant.parse("2026-10-16T00:00:00Z")));
        FeedEntry result=ContentFeedService.toEntry(ContentFeedSource.RUANYIFENG,raw);
        assertThat(result.getSourceUrl()).isEqualTo("https://www.ruanyifeng.com/blog/2026/10/example.html");
        assertThat(result.getTitle()).isEqualTo("AI tools & engineering");
        assertThat(result.getSourcePublishedAt().getHour()).isEqualTo(8);
        assertThat(result.getStatus()).isZero();
        assertThat(result.getUrlHash()).isEqualTo(ContentFeedService.toEntry(ContentFeedSource.RUANYIFENG,entry(result.getSourceUrl())).getUrlHash());
    }
    @Test void rejectsForeignAndScriptLinksAndKeepsUnknownDatesUnknown() {
        for(String url:List.of("javascript:alert(1)","https://github.blog.attacker.example/x","https://user@github.blog/x","https://github.blog:1234/x"))
            assertThat(ContentFeedService.toEntry(ContentFeedSource.GITHUB,entry(url))).isNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.GITHUB,entry("https://github.blog/example/" )).getSourcePublishedAt()).isNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.RUANYIFENG,entry("https://www.ruanyifeng.com/blog/../outside"))).isNull();
    }
    @Test void filtersMarketingButAllowsTechnicalCopilotArticles() {
        assertThat(ContentFeedSource.GITHUB.accepts("How we build agents", "AI")).isTrue();
        assertThat(ContentFeedSource.GITHUB.accepts("Copilot coding practices", "")).isTrue();
        assertThat(ContentFeedSource.GITHUB.accepts("Register for GitHub Universe", "AI")).isFalse();
        assertThat(ContentFeedSource.GITHUB.accepts("Get a discount on AI tools", "AI")).isFalse();
        assertThat(ContentFeedSource.GITHUB.accepts("New office opening", "Company news")).isFalse();
    }
    @Test void duplicateDoesNotAbortBatchAndNeverPublishes() throws Exception {
        when(client.fetch(ContentFeedSource.GITHUB)).thenReturn(List.of(entry("https://github.blog/one/"),entry("https://github.blog/two/")));
        when(mapper.insertEntry(any())).thenThrow(new DuplicateKeyException("duplicate")).thenReturn(1);
        var run=service.collect("github");
        assertThat(run.isSuccess()).isTrue(); assertThat(run.getDuplicates()).isEqualTo(1); assertThat(run.getInserted()).isEqualTo(1);
        verify(mapper,never()).insertLinkArticle(any()); verify(mapper).insertRun(run);
        verify(translations).translatePending("github");
    }
    @Test void failedSourceIsRecordedAndDailyQuotaStopsFetching() throws Exception {
        when(client.fetch(ContentFeedSource.GITHUB)).thenThrow(new IllegalStateException("private details"));
        var run=service.collect("github"); assertThat(run.isSuccess()).isFalse(); assertThat(run.getMessage()).doesNotContain("private details");
        verify(mapper).insertRun(run);
        when(mapper.countSince(any())).thenReturn(60L); clearInvocations(client);
        assertThat(service.collect("github").getInserted()).isZero(); verifyNoInteractions(client);
    }
    @Test void newEntryCapAndAgeFilterAreAppliedBeforeWriting() throws Exception {
        properties.setMaxNewPerSource(1);
        var old=entry("https://github.blog/old/"); old.setPublishedDate(Date.from(Instant.parse("2000-01-01T00:00:00Z")));
        when(client.fetch(ContentFeedSource.GITHUB)).thenReturn(List.of(old,entry("https://github.blog/new/"),entry("https://github.blog/extra/")));
        var run=service.collect("github"); assertThat(run.getFiltered()).isEqualTo(1); assertThat(run.getInserted()).isEqualTo(1);
        verify(mapper,times(1)).insertEntry(any());
    }
    @Test void sportsAllowlistedAliasesKeepSportPathsAndRejectForeignOrNonSportLinks() {
        var bbc=ContentFeedService.toEntry(ContentFeedSource.BBC_SPORT,entry("https://www.bbc.com/sport/football/articles/example?at_campaign=rss#top"));
        assertThat(bbc.getSourceUrl()).isEqualTo("https://www.bbc.com/sport/football/articles/example");
        assertThat(ContentFeedService.toEntry(ContentFeedSource.BBC_SPORT,entry("https://www.bbc.co.uk/news/example"))).isNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.BBC_SPORT,entry("https://www.bbc.co.uk.attacker.example/sport/x"))).isNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.ITHOME,entry("https://www.ithome.com/1/014/619.htm"))).isNotNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.ESPORTS_INSIDER,entry("https://esportsinsider.com/2026/10/event"))).isNotNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.ESPORTS_HOT,entry("https://news.google.com/rss/articles/CBMiWEFVX3lxTE1mRFpNYkZaZkhGV2lKLXRZZHlORXlVRnlqeERmZUd4Q0JhU3NYT1FqSDVkUWlGeE9NMklSc1YxTXZwczRoeC14UlhtOGVVb1c4eG96c2lhS3k?oc=5"))).isNotNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.ESPORTS_HOT,entry("https://www.gcores.com/articles/221015"))).isNotNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.CHINANEWS_SPORT,entry("https://www.chinanews.com.cn/ty/2026/10-24/10720527.shtml"))).isNotNull();
        assertThat(ContentFeedService.toEntry(ContentFeedSource.SSPAI,entry("https://sspai.com/post/115241"))).isNotNull();
    }
    @Test void newChannelsDoNotUseGithubTopicFilterAndSkipObviousShoppingOrBetting() {
        assertThat(ContentFeedSource.BBC_SPORT.accepts("World Cup qualifier result", "Football")).isTrue();
        assertThat(ContentFeedSource.ESPORTS_INSIDER.accepts("Team wins VALORANT Champions", "Esports")).isTrue();
        assertThat(ContentFeedSource.ESPORTS_INSIDER.accepts("Best betting odds for finals", "Betting")).isFalse();
        assertThat(ContentFeedSource.ITHOME.accepts("首发即国补：耳机 499 → 378 元", "")).isFalse();
        assertThat(ContentFeedSource.ITHOME.accepts("新款芯片正式发布", "")).isTrue();
        assertThat(ContentFeedSource.SSPAI.accepts("把墨水屏变成点餐助手", "")).isTrue();
    }
    @Test void sportsCollectionUsesItsOwnTranslationBatchAndChineseSourceSkipsModel() throws Exception {
        when(client.fetch(ContentFeedSource.BBC_SPORT)).thenReturn(List.of(entry("https://www.bbc.co.uk/sport/football/articles/example")));
        assertThat(service.collect("bbc_sport").getInserted()).isEqualTo(1);
        verify(translations).translatePending("bbc_sport"); clearInvocations(translations);
        when(client.fetch(ContentFeedSource.SSPAI)).thenReturn(List.of(entry("https://sspai.com/post/123")));
        assertThat(service.collect("sspai").getInserted()).isEqualTo(1); verifyNoInteractions(translations);
    }
    @Test void oneFastSourceCannotExhaustTheWholeDailyBudget() {
        when(mapper.countSince(any())).thenReturn(10L);
        when(mapper.countSourceSince(eq("ithome"),any())).thenReturn(10L);
        var run=service.collect("ithome"); assertThat(run.getInserted()).isZero();
        assertThat(run.isSkipped()).isTrue(); assertThat(run.isSuccess()).isFalse(); verify(mapper).insertRun(run);
        assertThat(run.getMessage()).contains("该来源"); verifyNoInteractions(client);
    }
    @Test void siteDailyCapIsSkippedRatherThanSuccessful() {
        when(mapper.countSince(any())).thenReturn(60L);
        var run=service.collect("github");
        assertThat(run.isSkipped()).isTrue(); assertThat(run.isSuccess()).isFalse(); verifyNoInteractions(client,translations);
        verify(mapper).insertRun(run);
    }
}
