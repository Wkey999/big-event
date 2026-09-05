package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Category;
import com.itheima.pojo.User;
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
    private final UserMapper userMapper;

    @Override
    public List<Category> list() {
        // 全站共享频道：所有人看到同一份分类池，做下拉与标签映射
        return categoryMapper.findAll();
    }

    @Override
    public void add(Category category) {
        // 频道制：只有管理员能新建频道
        assertAdmin();
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
        // 频道制：只有管理员能改名/改配置；分类可被所有人引用
        assertAdmin();
        assertNameAvailable(category.getCategoryName(), category.getId());
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        // 频道制：只有管理员能删除频道（不限该频道当初由谁创建）
        assertAdmin();
        // 名下仍有文章时不允许删除，否则这些文章的分类会失效
        if (articleMapper.countByCategoryId(id) > 0) {
            throw new RuntimeException("该分类下仍有文章，请先删除或转移文章");
        }
        categoryMapper.deleteLogical(id);
    }

    /**
     * 当前登录用户必须是管理员（user.role = 1），否则拒绝写操作
     */
    private void assertAdmin() {
        User user = userMapper.findById(ThreadLocalUtils.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
            throw new RuntimeException("需要管理员权限");
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
