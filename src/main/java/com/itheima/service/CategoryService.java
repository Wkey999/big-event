package com.itheima.service;

import com.itheima.pojo.Category;

import java.util.List;

public interface CategoryService {

    List<Category> list();

    void add(Category category);

    Category getById(Long id);

    void update(Category category);

    void delete(Long id);
}
