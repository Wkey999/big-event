package com.itheima.service;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CommunityMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.PostMediaDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private UserMapper userMapper;

    @Test
    void mediaReplacementStoresOrderedRowsAndSynchronizesCover() {
        CommunityService service = new CommunityService(communityMapper, articleMapper, userMapper);
        Article article = new Article();
        article.setId(7L);
        article.setUserId(42L);
        when(communityMapper.findArticleForUpdate(7L)).thenReturn(article);
        when(articleMapper.updateCoverImg(7L, "https://example.test/first.png")).thenReturn(1);
        PostMediaDTO first = media("https://example.test/first.png");
        PostMediaDTO second = media("https://example.test/second.png");

        service.replaceMedia(7L, List.of(first, second), 42L);

        verify(communityMapper).insertMedia(7L, first, 0);
        verify(communityMapper).insertMedia(7L, second, 1);
        verify(articleMapper).updateCoverImg(7L, "https://example.test/first.png");
    }

    @Test
    void topicReplacementOnlyChangesRelationsThatDiffer() {
        CommunityService service = new CommunityService(communityMapper, articleMapper, userMapper);
        when(communityMapper.listTopicIdsForArticle(7L)).thenReturn(List.of(1L, 2L));
        when(communityMapper.decrementTopicPostCount(1L)).thenReturn(1);
        when(communityMapper.insertTopicIfAbsent("new topic")).thenReturn(1);
        when(communityMapper.findTopicIdByName("new topic")).thenReturn(3L);
        when(communityMapper.insertTopicIfAbsent("existing")).thenReturn(0);
        when(communityMapper.findTopicIdByName("existing")).thenReturn(2L);

        service.applyOptionalFields(7L, null, List.of(" new topic ", "existing"), "");

        verify(communityMapper).deletePostTopic(7L, 1L);
        verify(communityMapper).decrementTopicPostCount(1L);
        verify(communityMapper).insertPostTopic(7L, 3L);
        verify(communityMapper).incrementTopicPostCount(3L);
        verify(communityMapper, never()).deletePostTopic(7L, 2L);
        verify(communityMapper, never()).incrementTopicPostCount(2L);
    }

    @Test
    void rejectsMoreThanFiveTopicsBeforeWriting() {
        CommunityService service = new CommunityService(communityMapper, articleMapper, userMapper);

        assertThatThrownBy(() -> service.applyOptionalFields(7L, null,
                List.of("a", "b", "c", "d", "e", "f"), ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("一篇帖子最多添加5个话题");
        verify(communityMapper, never()).listTopicIdsForArticle(7L);
    }

    @Test
    void legacyCoverIsRepresentedAsFirstMediaWhenNoMediaWasSubmitted() {
        CommunityService service = new CommunityService(communityMapper, articleMapper, userMapper);
        when(communityMapper.updateFirstMediaUrl(7L, "https://example.test/cover.png")).thenReturn(0);

        service.applyOptionalFields(7L, null, null, "https://example.test/cover.png");

        verify(communityMapper).insertMedia(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(item ->
                        "https://example.test/cover.png".equals(item.getUrl())
                                && Integer.valueOf(1).equals(item.getMediaType())),
                org.mockito.ArgumentMatchers.eq(0));
    }

    private static PostMediaDTO media(String url) {
        PostMediaDTO media = new PostMediaDTO();
        media.setUrl(url);
        return media;
    }
}
