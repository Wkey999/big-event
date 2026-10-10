package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserActionMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.Category;
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private ArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalUtils.setUserId(USER_ID);
        service = new ArticleServiceImpl(articleMapper, categoryMapper, userMapper, userActionMapper);
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

        service.list(1, 5, null, "0");
        service.list(1, 5, null, "草稿");

        verify(articleMapper, times(2)).countByCondition(USER_ID, null, 0);
    }

    @Test
    void listRejectsUnknownStateInsteadOfSilentlyRemovingFilter() {
        assertThatThrownBy(() -> service.list(1, 5, null, "archived"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文章状态仅支持 0/草稿 或 1/已发布");
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
}
