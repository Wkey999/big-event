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
    private final FeedPublishingService service=new FeedPublishingService(mapper,users,categories);
    @BeforeEach void admin() {
        ThreadLocalUtils.setUserId(1L); User admin=new User(); admin.setRole(1); admin.setStatus(1); admin.setLoginEnabled(1);
        when(users.findById(1L)).thenReturn(admin);
    }
    @AfterEach void clear() { ThreadLocalUtils.clear(); }
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
