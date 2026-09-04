package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.Category;
import com.itheima.pojo.PageBean;
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

    @Override
    public void add(ArticleAddDTO dto) {
        Long userId = ThreadLocalUtils.getUserId();
        assertCategoryOwned(dto.getCategoryId(), userId);
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
        assertCategoryOwned(dto.getCategoryId(), userId);
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
     * categoryId 由客户端传，不校验就能把文章挂到别人的分类上（越权写入 + 数据串号）。
     * 分类不存在与分类属于他人返回同一句话，避免这个接口被用来探测别人的分类 id。
     * findById 自带 deleted = 0 条件，所以已软删的分类也不能再挂。
     */
    private void assertCategoryOwned(Long categoryId, Long userId) {
        Category category = categoryMapper.findById(categoryId);
        if (category == null || !category.getUserId().equals(userId)) {
            throw new RuntimeException("分类不存在或无权使用");
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
        // 归属校验：只能查看自己的文章（含草稿），防止越权读取
        if (!article.getUserId().equals(ThreadLocalUtils.getUserId())) {
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

        Long total = articleMapper.countByCondition(userId, categoryId, stateInt);
        List<Article> items = List.of();
        if (total > 0) {
            int offset = (pageNum - 1) * pageSize;
            items = articleMapper.findByCondition(userId, categoryId, stateInt, offset, pageSize);
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
