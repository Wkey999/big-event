package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private static final long ADMIN_ID = 7L;

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ArticleMapper articleMapper;
    @Mock
    private UserMapper userMapper;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalUtils.setUserId(ADMIN_ID);
        service = new CategoryServiceImpl(categoryMapper, articleMapper, userMapper);
        User administrator = new User();
        administrator.setRole(1);
        when(userMapper.findById(ADMIN_ID)).thenReturn(administrator);
    }

    @AfterEach
    void clearUserContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void updateRejectsMissingOrDeletedCategory() {
        Category requested = new Category();
        requested.setId(99L);
        requested.setCategoryName("technology");
        when(categoryMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(requested))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
        verify(categoryMapper, never()).update(any());
    }

    @Test
    void deleteRejectsMissingOrDeletedCategory() {
        when(categoryMapper.findByIdForUpdate(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
        verify(articleMapper, never()).countByCategoryId(99L);
        verify(categoryMapper, never()).deleteLogical(99L);
    }

    @Test
    void deleteRejectsWhenLogicalDeleteAffectsNoRows() {
        Category category = new Category();
        category.setId(99L);
        when(categoryMapper.findByIdForUpdate(99L)).thenReturn(category);
        when(articleMapper.countByCategoryId(99L)).thenReturn(0L);
        when(categoryMapper.deleteLogical(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
    }
}
