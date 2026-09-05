package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.Category;
import com.itheima.service.CategoryService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<Category> list() {
        // 全站共享分类：所有人看到同一份分类池，做下拉与标签映射
        return categoryMapper.findAll();
    }

    @Override
    public void add(Category category) {
        Long userId = ThreadLocalUtils.getUserId();
        category.setUserId(userId);
        assertNameAvailable(category.getCategoryName(), null);
        categoryMapper.insert(category);
    }

    @Override
    public Category getById(Long id) {
        Category category = categoryMapper.findById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        // 分类是共享元数据，详情开放给所有登录用户
        return category;
    }

    @Override
    public void update(Category category) {
        // 存在性 + 归属：共享分类可被所有人引用，但只有创建者能改名/改配置
        assertOwned(category.getId());
        assertNameAvailable(category.getCategoryName(), category.getId());
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        assertOwned(id);
        // 名下仍有文章时不允许删除，否则这些文章的分类会失效
        if (articleMapper.countByCategoryId(id) > 0) {
            throw new RuntimeException("该分类下仍有文章，请先删除或转移文章");
        }
        categoryMapper.deleteById(id, ThreadLocalUtils.getUserId());
    }

    /**
     * 校验分类存在且属于当前用户（更新/删除的写权限边界）
     */
    private void assertOwned(Long id) {
        Category existing = categoryMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("分类不存在");
        }
        if (!existing.getUserId().equals(ThreadLocalUtils.getUserId())) {
            throw new RuntimeException("无权操作该分类");
        }
    }

    /**
     * 分类名在全站唯一；excludeId 为更新时自身 id（改名不与自己冲突），新增时传 null
     */
    private void assertNameAvailable(String categoryName, Long excludeId) {
        Category existing = categoryMapper.findByName(categoryName);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new RuntimeException("分类名称已存在");
        }
    }
}
