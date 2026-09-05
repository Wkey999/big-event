package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.Category;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.User;
import com.itheima.service.ArticleService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;

    @Override
    public void add(ArticleAddDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        assertCategoryExists(dto.getCategoryId());
        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCoverImg(emptyIfNull(dto.getCoverImg()));
        article.setSummary(emptyIfNull(dto.getSummary()));
        article.setCategoryId(dto.getCategoryId());
        article.setState(dto.getState());
        article.setUserId(userId);
        articleMapper.insert(article);
    }

    @Override
    public void update(ArticleUpdateDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        assertCategoryExists(dto.getCategoryId());
        Article article = new Article();
        article.setId(dto.getId());
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCoverImg(emptyIfNull(dto.getCoverImg()));
        article.setSummary(emptyIfNull(dto.getSummary()));
        article.setCategoryId(dto.getCategoryId());
        article.setState(dto.getState());
        // 作者不可改：update 的 WHERE 带 user_id，越权/不存在时影响 0 行
        article.setUserId(userId);
        int rows = articleMapper.update(article);
        // update 带 user_id 条件：0 行 = 文章不存在或不属于当前用户
        if (rows == 0) {
            throw new RuntimeException("更新失败：文章不存在或无权操作");
        }
    }

    /**
     * categoryId 由客户端传。分类已是全站共享池，任何人发布/编辑时都可引用任意
     * 存在的分类；分类不存在与已软删都会在此被拦下（findById 自带 deleted = 0）。
     */
    private void assertCategoryExists(Long categoryId) {
        if (categoryMapper.findById(categoryId) == null) {
            throw new RuntimeException("分类不存在");
        }
    }

    /**
     * cover_img/summary 列是 NOT NULL DEFAULT ''，但 mapper 显式写了这两列，
     * 传 null 会撞非空约束（列默认值只在 INSERT 省略该列时才生效）
     */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    @Override
    public Article getById(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }
        // 已发布 → 全员可看；草稿 → 仅作者本人，但管理员可纵览（含他人草稿）
        boolean published = Integer.valueOf(1).equals(article.getState());
        Long userId = ThreadLocalUtils.getUserId();
        User me = userMapper.findById(userId);
        boolean admin = me != null && Integer.valueOf(1).equals(me.getRole());
        if (!published && !article.getUserId().equals(userId) && !admin) {
            throw new RuntimeException("无权访问该文章");
        }
        articleMapper.incrementViewCount(id);
        return article;
    }

    @Override
    public void delete(Long id) {
        int rows = articleMapper.deleteById(id, ThreadLocalUtils.getUserId());
        // delete 带 user_id 条件：0 行 = 文章不存在或不属于当前用户
        if (rows == 0) {
            throw new RuntimeException("删除失败：文章不存在或无权操作");
        }
    }

    /**
     * 条件分页查询
     * 先查总条数，再查当前页数据，封装成 PageBean 返回
     */
    @Override
    public PageBean<Article> list(Integer pageNum, Integer pageSize, Long categoryId, String state) {
        Long userId = ThreadLocalUtils.getUserId();
        Integer stateInt = parseState(state);

        // 管理员纵览全站（userId 传 null 即不过滤作者，草稿也可见）；普通用户只看自己的
        User me = userMapper.findById(userId);
        boolean admin = me != null && Integer.valueOf(1).equals(me.getRole());
        Long queryUserId = admin ? null : userId;

        Long total = articleMapper.countByCondition(queryUserId, categoryId, stateInt);
        List<Article> items = List.of();
        if (total > 0) {
            int offset = (pageNum - 1) * pageSize;
            items = articleMapper.findByCondition(queryUserId, categoryId, stateInt, offset, pageSize);
        }
        return new PageBean<>(total, items);
    }

    private Integer parseState(String state) {
        if ("草稿".equals(state)) {
            return 0;
        }
        if ("已发布".equals(state)) {
            return 1;
        }
        return null;
    }
}
