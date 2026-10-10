package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.BrowseLogMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.BrowseLogDTO;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowseLogServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long ARTICLE_ID = 81L;

    @Mock
    private BrowseLogMapper browseLogMapper;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private UserMapper userMapper;

    private BrowseLogServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalUtils.setUserId(USER_ID);
        service = new BrowseLogServiceImpl(browseLogMapper, articleMapper, userMapper);
        Article article = new Article();
        article.setId(ARTICLE_ID);
        article.setCategoryId(7L);
        article.setState(1);
        when(articleMapper.findById(ARTICLE_ID)).thenReturn(article);
    }

    @AfterEach
    void clearUserContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void recordsFeedModeAndScopesTheDeduplicationLookupToThatMode() {
        BrowseLogDTO dto = new BrowseLogDTO();
        dto.setArticleId(ARTICLE_ID);
        dto.setDwellMs(0);
        dto.setMode("recommend");
        when(browseLogMapper.findRecentId(eq(USER_ID), eq(ARTICLE_ID), eq("recommend"), any(LocalDateTime.class)))
                .thenReturn(null);

        service.report(dto);

        verify(browseLogMapper).insert(USER_ID, ARTICLE_ID, 7L, 0, "recommend");
    }

    @Test
    void defaultsMissingModeToLatestForOldClients() {
        BrowseLogDTO dto = new BrowseLogDTO();
        dto.setArticleId(ARTICLE_ID);
        dto.setDwellMs(1200);
        when(browseLogMapper.findRecentId(eq(USER_ID), eq(ARTICLE_ID), eq("latest"), any(LocalDateTime.class)))
                .thenReturn(901L);

        service.report(dto);

        verify(browseLogMapper).updateDwell(901L, 1200);
    }
}
