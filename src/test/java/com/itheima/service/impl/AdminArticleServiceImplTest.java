package com.itheima.service.impl;

import com.itheima.mapper.AdminMapper;
import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.AdminArticleListItem;
import com.itheima.service.ArticleService;
import com.itheima.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminArticleServiceImplTest {

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private ArticleService articleService;

    @Test
    void articleListEscapesLikeWildcardsAndClampsLargePageOffsets() {
        AdminArticleServiceImpl service = new AdminArticleServiceImpl(adminMapper, articleMapper, articleService,
                mock(CommunityService.class));
        AdminArticleListItem row = new AdminArticleListItem();
        row.setId(4L);
        row.setTitle("literal wildcard");
        when(adminMapper.countAdminArticles(1, 9L, 2L, "50\\%\\_off")).thenReturn(1L);
        when(adminMapper.findAdminArticles(1, 9L, 2L, "50\\%\\_off", 9_999_900, 100))
                .thenReturn(List.of(row));

        var page = service.list(Integer.MAX_VALUE, 500, 1, 9L, 2L, "50%_off");

        assertThat(page.getItems()).containsExactly(row);
        assertThat(page.getTotal()).isEqualTo(1L);
        verify(adminMapper).findAdminArticles(1, 9L, 2L, "50\\%\\_off", 9_999_900, 100);
    }
}
