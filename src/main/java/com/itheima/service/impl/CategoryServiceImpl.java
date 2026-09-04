package com.itheima.service.impl;

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

    @Override
    public List<Category> list() {
        return categoryMapper.findByUserId(ThreadLocalUtils.getUserId());
    }

    @Override
    public void add(Category category) {
        Long userId = ThreadLocalUtils.getUserId();
        category.setUserId(userId);
        assertNameAvailable(userId, category.getCategoryName(), null);
        categoryMapper.insert(category);
    }

    @Override
    public Category getById(Long id) {
        Category category = categoryMapper.findById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        // 归属校验：只能查看自己的分类，防止越权读取他人数据
        if (!category.getUserId().equals(ThreadLocalUtils.getUserId())) {
            throw new RuntimeException("无权访问该分类");
        }
        return category;
    }

    @Override
    public void update(Category category) {
        // 先校验分类存在且属于当前用户
        Category existing = getById(category.getId());
        assertNameAvailable(existing.getUserId(), category.getCategoryName(), category.getId());
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        // 先校验分类存在且属于当前用户，越权/不存在直接抛错而非静默成功
        getById(id);
        categoryMapper.deleteById(id, ThreadLocalUtils.getUserId());
    }

    /**
     * 分类名在同一用户下唯一；excludeId 为更新时自身 id（改名不与自己冲突），新增时传 null
     */
    private void assertNameAvailable(Long userId, String categoryName, Long excludeId) {
        Category existing = categoryMapper.findByUserIdAndName(userId, categoryName);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new RuntimeException("分类名称已存在");
        }
    }
}
