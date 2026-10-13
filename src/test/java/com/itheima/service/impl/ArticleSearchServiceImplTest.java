package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.PageBean;
import com.itheima.service.SearchLogService;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleSearchServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private SearchLogService searchLogService;

    private ArticleSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalUtils.setUserId(31L);
        service = new ArticleSearchServiceImpl(articleMapper, searchLogService);
    }

    @AfterEach
    void clearContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void appliesKeywordAuthorCategoryTimeAndOrderFiltersAndLogsQuery() {
        ArticleSearchRow row = new ArticleSearchRow();
        row.setId(51L);
        row.setTitle("Spring search");
        row.setSummary("A short overview");
        when(articleMapper.countPublishedSearch(eq("Spring"), eq(4L), eq(8L), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(articleMapper.searchPublished(eq("Spring"), eq(4L), eq(8L), any(LocalDateTime.class),
                eq("popular"), eq(0), eq(10))).thenReturn(List.of(row));

        PageBean<ArticleSearchRow> result = service.search(" Spring ", 1, 10, 4L, 8L, "7d", "popular");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getItems()).extracting(ArticleSearchRow::getId).containsExactly(51L);
        assertThat(result.getItems().get(0).getHitField()).isEqualTo("title");
        assertThat(result.getItems().get(0).getHitSnippet()).isEqualTo("Spring search");
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(articleMapper).countPublishedSearch(eq("Spring"), eq(4L), eq(8L), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isAfter(LocalDateTime.now().minusDays(7).minusSeconds(2));
        verify(searchLogService).record(eq(31L), eq("Spring"), eq(4L), eq("7d"), eq(1L), anyLong());
    }

    @Test
    void rejectsUnsupportedTimeRangeBeforeQueryingDatabase() {
        assertThatThrownBy(() -> service.search("topic", 1, 10, null, null, "forever", "latest"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timeRange 仅支持 all/1d/7d/30d");
        verify(articleMapper, never()).countPublishedSearch(any(), any(), any(), any());
    }

    @Test
    void escapesLikeWildcardsBeforeBindingKeyword() {
        when(articleMapper.countPublishedSearch(eq("50\\%\\_off"), isNull(), isNull(), isNull())).thenReturn(0L);

        PageBean<ArticleSearchRow> result = service.search("50%_off", 1, 10, null, null, "all", "latest");

        assertThat(result.getTotal()).isZero();
        verify(articleMapper).countPublishedSearch(eq("50\\%\\_off"), isNull(), isNull(), isNull());
    }

    @Test
    void summarySnippetIsPlainTextAndBounded() {
        ArticleSearchRow row = new ArticleSearchRow();
        row.setId(62L);
        row.setTitle("Unrelated headline");
        row.setSummary("prefix <b>Spring</b> summary with &amp; escaped HTML tags and enough extra words to exceed sixty characters");
        when(articleMapper.countPublishedSearch(eq("Spring"), isNull(), isNull(), isNull())).thenReturn(1L);
        when(articleMapper.searchPublished(eq("Spring"), isNull(), isNull(), isNull(), eq("latest"), eq(0), eq(10)))
                .thenReturn(List.of(row));

        PageBean<ArticleSearchRow> result = service.search("Spring", 1, 10, null, null, "all", "latest");

        assertThat(result.getItems().get(0).getHitField()).isEqualTo("summary");
        assertThat(result.getItems().get(0).getHitSnippet()).contains("Spring").doesNotContain("<b>");
        assertThat(result.getItems().get(0).getHitSnippet().length()).isLessThanOrEqualTo(60);
    }

    @Test
    void clampsHugePageNumberBeforeCalculatingOffset() {
        when(articleMapper.countPublishedSearch(eq("topic"), isNull(), isNull(), isNull())).thenReturn(1L);
        when(articleMapper.searchPublished(eq("topic"), isNull(), isNull(), isNull(), eq("latest"), eq(999_990), eq(10)))
                .thenReturn(List.of());

        service.search("topic", Integer.MAX_VALUE, 10, null, null, "all", "latest");

        verify(articleMapper).searchPublished(eq("topic"), isNull(), isNull(), isNull(), eq("latest"), eq(999_990), eq(10));
    }
}
