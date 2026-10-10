package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.Category;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.ProfileInterest;
import com.itheima.pojo.User;
import com.itheima.pojo.UserProfile;
import com.itheima.service.UserProfileService;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    private static final long USER_ID = 42L;

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserActionMapper userActionMapper;
    @Mock
    private UserProfileService userProfileService;

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalUtils.setUserId(USER_ID);
        service = new ArticleServiceImpl(articleMapper, categoryMapper, userMapper, userActionMapper, userProfileService);
        User ordinaryUser = new User();
        ordinaryUser.setRole(0);
        lenient().when(userMapper.findById(USER_ID)).thenReturn(ordinaryUser);
    }

    @AfterEach
    void clearUserContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void listAcceptsNumericAndLegacyChineseStateValues() {
        when(articleMapper.countByCondition(USER_ID, null, 0)).thenReturn(0L);

        service.list(1, 5, null, "0", "latest");
        service.list(1, 5, null, "草稿", "latest");

        verify(articleMapper, times(2)).countByCondition(USER_ID, null, 0);
    }

    @Test
    void listRejectsUnknownStateInsteadOfSilentlyRemovingFilter() {
        assertThatThrownBy(() -> service.list(1, 5, null, "archived", "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文章状态仅支持 0/草稿 或 1/已发布");
    }

    @Test
    void recommendationRanksArticlesByTheCurrentUsersCategoryWeights() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(2);
        Article preferred = article(1L, 10L, createdAt);
        Article other = article(2L, 20L, createdAt);
        when(articleMapper.countByCondition(null, null, 1)).thenReturn(2L);
        when(articleMapper.findByCondition(isNull(), isNull(), eq(1), eq(0), eq(100)))
                .thenReturn(new ArrayList<>(List.of(other, preferred)));
        when(userProfileService.get(USER_ID)).thenReturn(profile(10L, 0.9, 5));

        PageBean<Article> result = service.list(1, 5, null, "已发布", "recommend");

        assertThat(result.getItems()).extracting(Article::getId).containsExactly(1L, 2L);
        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void recommendationFallsBackToDatabaseTimeOrderWithoutEnoughProfileData() {
        Article newest = article(2L, 20L, LocalDateTime.now().minusHours(1));
        Article older = article(1L, 10L, LocalDateTime.now().minusDays(2));
        when(articleMapper.countByCondition(null, null, 1)).thenReturn(2L);
        when(articleMapper.findByCondition(isNull(), isNull(), eq(1), eq(0), eq(100)))
                .thenReturn(new ArrayList<>(List.of(newest, older)));
        when(userProfileService.get(USER_ID)).thenReturn(profile(10L, 0.9, 2));

        PageBean<Article> result = service.list(1, 5, null, null, "recommend");

        assertThat(result.getItems()).extracting(Article::getId).containsExactly(2L, 1L);
    }

    @Test
    void recommendationUsesOneFixedWindowAndReturnsAtMostThirtyArticles() {
        List<Article> candidates = IntStream.rangeClosed(1, 35)
                .mapToObj(id -> article((long) id, 10L, LocalDateTime.now().minusDays(1)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        when(articleMapper.countByCondition(null, null, 1)).thenReturn(35L);
        when(articleMapper.findByCondition(isNull(), isNull(), eq(1), eq(0), eq(100))).thenReturn(candidates);
        when(userProfileService.get(USER_ID)).thenReturn(profile(10L, 1.0, 10));

        PageBean<Article> result = service.list(4, 100, null, "已发布", "recommend");

        assertThat(result.getItems()).hasSize(30);
        assertThat(result.getTotal()).isEqualTo(35L);
        verify(articleMapper).findByCondition(null, null, 1, 0, 100);
    }

    @Test
    void recommendationRejectsDraftFilter() {
        assertThatThrownBy(() -> service.list(1, 5, null, "草稿", "recommend"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("推荐模式仅支持已发布文章");
    }

    @Test
    void updateFailsWhenMapperFindsNoActiveArticle() {
        User administrator = new User();
        administrator.setRole(1);
        when(userMapper.findById(USER_ID)).thenReturn(administrator);

        Category category = new Category();
        category.setId(7L);
        when(categoryMapper.findByIdForUpdate(7L)).thenReturn(category);
        when(articleMapper.updateAny(any())).thenReturn(0);

        ArticleUpdateDTO dto = new ArticleUpdateDTO();
        dto.setId(99L);
        dto.setTitle("title");
        dto.setContent("content");
        dto.setCategoryId(7L);
        dto.setState(1);

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("更新失败：文章不存在或无权操作");
        verify(articleMapper).updateAny(any());
    }

    private static Article article(Long id, Long categoryId, LocalDateTime createTime) {
        Article article = new Article();
        article.setId(id);
        article.setCategoryId(categoryId);
        article.setState(1);
        article.setViewCount(10);
        article.setCreateTime(createTime);
        return article;
    }

    private static UserProfile profile(Long categoryId, double weight, int sampleSize) {
        ProfileInterest interest = new ProfileInterest();
        interest.setCategoryId(categoryId);
        interest.setWeight(BigDecimal.valueOf(weight));
        UserProfile profile = new UserProfile();
        profile.setSampleSize(sampleSize);
        profile.setInterests(List.of(interest));
        return profile;
    }
}
