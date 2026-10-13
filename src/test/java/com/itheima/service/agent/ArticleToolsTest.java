package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.ArticleQueryResult;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.Category;
import com.itheima.service.BrowseLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleToolsTest {

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private BrowseLogService browseLogService;

    private ArticleTools tools;

    @BeforeEach
    void setUp() {
        tools = new ArticleTools(articleMapper, categoryMapper, browseLogService);
    }

    @Test
    void capsResultsAtTwentyAndSignalsTruncation() {
        List<ArticleSearchRow> rows = new ArrayList<>();
        for (long id = 1; id <= 21; id++) {
            ArticleSearchRow row = new ArticleSearchRow();
            row.setId(id);
            row.setTitle("Article " + id);
            rows.add(row);
        }
        when(articleMapper.searchPublished("Spring", null, null, null, "latest", 0, 21)).thenReturn(rows);

        ArticleQueryResult result = tools.queryArticles("Spring", null, 500, "all", "latest");

        assertThat(result.items()).hasSize(20);
        assertThat(result.truncated()).isTrue();
        verify(articleMapper).searchPublished("Spring", null, null, null, "latest", 0, 21);
        verify(browseLogService, times(20))
                .report(argThat(dto -> "ask".equals(dto.getMode()) && dto.getDwellMs() == 0));
    }

    @Test
    void unknownCategoryReturnsNoResultsWithoutSearchingOtherCategories() {
        when(categoryMapper.findByName("unknown")).thenReturn(null);

        ArticleQueryResult result = tools.queryArticles("Spring", "unknown", 10, "all", "latest");

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(articleMapper, never()).searchPublished(
                eq("Spring"), isNull(), isNull(), isNull(), eq("latest"), eq(0), eq(11));
    }

    @Test
    void agentToolPushesTimeAndOrderFiltersIntoThePublishedSqlQuery() {
        Category category = new Category();
        category.setId(12L);
        when(categoryMapper.findByName("Sports")).thenReturn(category);
        when(articleMapper.searchPublished(eq("final"), eq(12L), isNull(), any(LocalDateTime.class),
                eq("popular"), eq(0), eq(11))).thenReturn(List.of());

        ArticleQueryResult result = tools.queryArticles("final", "Sports", 10, "1d", "popular");

        assertThat(result.items()).isEmpty();
        org.mockito.ArgumentCaptor<LocalDateTime> since = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(articleMapper).searchPublished(eq("final"), eq(12L), isNull(), since.capture(),
                eq("popular"), eq(0), eq(11));
        assertThat(since.getValue()).isAfter(LocalDateTime.now().minusDays(1).minusSeconds(2));
    }
}
