package com.itheima.service.feed;
import com.itheima.mapper.*;
import com.itheima.pojo.*;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FeedPublishingServiceTest {
    private final ContentFeedMapper mapper=mock(ContentFeedMapper.class);
    private final UserMapper users=mock(UserMapper.class);
    private final CategoryMapper categories=mock(CategoryMapper.class);
    private final com.itheima.config.ContentFeedProperties properties=new com.itheima.config.ContentFeedProperties();
    private final FeedPublishPolicy policy=new FeedPublishPolicy(properties);
    private final FeedPublishingService service=new FeedPublishingService(mapper,users,categories,policy);
    @BeforeEach void admin() {
        ThreadLocalUtils.setUserId(1L); User admin=new User(); admin.setRole(1); admin.setStatus(1); admin.setLoginEnabled(1);
        when(users.findById(1L)).thenReturn(admin);
    }
    @AfterEach void clear() { ThreadLocalUtils.clear(); }
    private FeedEntry automaticEntry(String sourceKey,String url) {
        var entry=new FeedEntry(); entry.setStatus(0); entry.setSourceKey(sourceKey); entry.setTitle("官方项目更新");
        entry.setSourceUrl(url); entry.setAutoPublishRequested(1); entry.setTranslatedTitle("官方项目更新");
        when(mapper.lockEntry(2L)).thenReturn(entry); properties.setAutoPublishEnabled(true);
        return entry;
    }
    @Test void schedulerPublishesWithoutAnAdminSessionAndRecordsAutomaticMode() {
        automaticEntry("github","https://github.blog/engineering/agents/"); ThreadLocalUtils.clear();
        var category=new Category(); category.setId(3L); category.setStatus(1); category.setCategoryName("AI/技术热点");
        when(categories.findByName("AI/技术热点")).thenReturn(category); when(categories.findByIdForUpdate(3L)).thenReturn(category);
        when(users.lockActiveAdminIds()).thenReturn(List.of(1L));
        var bot=new User(); bot.setId(4L); bot.setRole(0); bot.setUserType(1); bot.setLoginEnabled(0); bot.setStatus(1);
        when(users.findByUsername("rss_github")).thenReturn(bot);
        doAnswer(call -> { ((Article)call.getArgument(0)).setId(5L); return 1; }).when(mapper).insertLinkArticle(any());
        when(mapper.publishAutomatically(2L,5L)).thenReturn(1);
        assertThat(service.publishAutomatically(2L)).isTrue();
        verify(mapper).publishAutomatically(2L,5L); verify(mapper,never()).review(anyLong(),anyInt(),any(),any());
    }
    @Test void historicalIgnoredAndAlreadyPublishedEntriesAreNeverAutoPublished() {
        var entry=automaticEntry("github","https://github.blog/engineering/agents/");
        entry.setAutoPublishRequested(0); assertThat(service.publishAutomatically(2L)).isFalse();
        entry.setAutoPublishRequested(1); entry.setStatus(2); assertThat(service.publishAutomatically(2L)).isFalse();
        entry.setStatus(1); assertThat(service.publishAutomatically(2L)).isFalse();
        verify(mapper,never()).insertLinkArticle(any());
    }
    @Test void disabledOrNonWhitelistedSourceCannotUseSchedulerBypass() {
        automaticEntry("sspai","https://sspai.com/post/123");
        assertThatThrownBy(() -> service.publishAutomatically(2L)).hasMessageContaining("白名单");
        automaticEntry("github","https://github.blog/engineering/agents/"); properties.setAutoPublishEnabled(false);
        assertThatThrownBy(() -> service.publishAutomatically(2L)).hasMessageContaining("白名单");
        verify(mapper,never()).insertLinkArticle(any());
    }
    @Test void untranslatedInvalidLinkOrMissingCategoryStaysPending() {
        var entry=automaticEntry("github","https://github.blog/engineering/agents/"); entry.setTranslatedTitle(null);
        assertThatThrownBy(() -> service.publishAutomatically(2L)).hasMessageContaining("中文标题");
        entry.setTranslatedTitle("项目更新"); entry.setSourceUrl("https://evil.example/news");
        assertThatThrownBy(() -> service.publishAutomatically(2L)).hasMessageContaining("链接");
        entry.setSourceUrl("https://github.blog/engineering/agents/");
        assertThatThrownBy(() -> service.publishAutomatically(2L)).hasMessageContaining("频道");
        verify(mapper,never()).insertLinkArticle(any());
    }
    @Test void repeatPublishDoesNotInsertAnotherArticle() {
        FeedEntry entry=new FeedEntry(); entry.setStatus(1); entry.setArticleId(8L); when(mapper.lockEntry(2L)).thenReturn(entry);
        assertThat(service.publish(2L,3L)).isEqualTo(8L); verify(mapper,never()).insertLinkArticle(any());
    }
    @Test void ordinaryUserCannotPublish() {
        User ordinary=new User(); ordinary.setRole(0); when(users.findById(1L)).thenReturn(ordinary);
        assertThatThrownBy(() -> service.publish(2L,3L)).hasMessage("需要管理员权限"); verifyNoInteractions(mapper);
    }
    @Test void reviewCreatesLinkOnlyUnderNonLoginBotAndPreservesMetadata() {
        FeedEntry entry=new FeedEntry(); entry.setStatus(0); entry.setSourceKey("github"); entry.setTitle("A".repeat(80));
        entry.setTranslatedTitle("中".repeat(80));
        when(mapper.lockEntry(2L)).thenReturn(entry); when(users.lockActiveAdminIds()).thenReturn(List.of(1L));
        when(mapper.review(eq(2L),eq(1),anyLong(),eq(1L))).thenReturn(1);
        Category category=new Category(); category.setStatus(1); when(categories.findByIdForUpdate(3L)).thenReturn(category);
        User bot=new User(); bot.setId(4L); bot.setRole(0); bot.setUserType(1); bot.setLoginEnabled(0); bot.setStatus(1);
        when(users.findByUsername("rss_github")).thenReturn(bot);
        String reviewed="人工修订的中文".repeat(10);
        doAnswer(call -> { Article a=call.getArgument(0); a.setId(5L); assertThat(a.getTitle()).isEqualTo(reviewed.substring(0,50)); assertThat(a.getContent()).isNull(); assertThat(a.getUserId()).isEqualTo(4L); return 1; }).when(mapper).insertLinkArticle(any());
        assertThat(service.publish(2L,3L,reviewed)).isEqualTo(5L); assertThat(entry.getTitle()).hasSize(80); assertThat(entry.getTranslatedTitle()).isEqualTo(reviewed); verify(mapper).insertArticleSource(entry); verify(mapper).review(2L,1,5L,1L);
    }
    @Test void githubWithoutChineseTitleCannotBePublished() {
        FeedEntry entry=new FeedEntry(); entry.setStatus(0); entry.setSourceKey("github"); entry.setTitle("English title");
        when(mapper.lockEntry(2L)).thenReturn(entry);
        assertThatThrownBy(() -> service.publish(2L,3L)).hasMessageContaining("中文标题");
        verify(mapper,never()).insertLinkArticle(any());
    }
}
