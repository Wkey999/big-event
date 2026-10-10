package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.ArticleQueryResult;
import com.itheima.pojo.ArticleSearchRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleToolsTest {

    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private CategoryMapper categoryMapper;

    private ArticleTools tools;

    @BeforeEach
    void setUp() {
        tools = new ArticleTools(articleMapper, categoryMapper);
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
        when(articleMapper.searchPublished("Spring", null, 21)).thenReturn(rows);

        ArticleQueryResult result = tools.queryArticles("Spring", null, 500);

        assertThat(result.items()).hasSize(20);
        assertThat(result.truncated()).isTrue();
        verify(articleMapper).searchPublished("Spring", null, 21);
    }

    @Test
    void unknownCategoryReturnsNoResultsWithoutSearchingOtherCategories() {
        when(categoryMapper.findByName("unknown")).thenReturn(null);

        ArticleQueryResult result = tools.queryArticles("Spring", "unknown", 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(articleMapper, never()).searchPublished(eq("Spring"), isNull(), eq(11));
    }
}
