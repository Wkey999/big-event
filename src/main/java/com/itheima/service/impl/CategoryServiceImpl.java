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
        category.setUserId(ThreadLocalUtils.getUserId());
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
        getById(category.getId());
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        // 先校验分类存在且属于当前用户，越权/不存在直接抛错而非静默成功
        getById(id);
        categoryMapper.deleteById(id, ThreadLocalUtils.getUserId());
    }
}
