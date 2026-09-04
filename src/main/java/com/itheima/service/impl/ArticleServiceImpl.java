package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
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

    @Override
    public void add(ArticleAddDTO dto) {
        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setContent(dto.getContent());
        article.setCoverImg(dto.getCoverImg());
        article.setSummary(dto.getSummary());
        article.setCategoryId(dto.getCategoryId());
        article.setState(dto.getState());
        article.setUserId(ThreadLocalUtils.getUserId());
        articleMapper.insert(article);
    }

    @Override
    public void update(Article article) {
        article.setUserId(ThreadLocalUtils.getUserId());
        int rows = articleMapper.update(article);
        // update 带 user_id 条件：0 行 = 文章不存在或不属于当前用户
        if (rows == 0) {
            throw new RuntimeException("更新失败：文章不存在或无权操作");
        }
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
