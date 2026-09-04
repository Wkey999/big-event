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
        return categoryMapper.findById(id);
    }

    @Override
    public void update(Category category) {
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        categoryMapper.deleteById(id, ThreadLocalUtils.getUserId());
    }
}
