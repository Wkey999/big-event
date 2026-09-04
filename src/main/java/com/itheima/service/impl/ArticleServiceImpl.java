package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.Article;
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
    public void add(Article article) {
        article.setUserId(ThreadLocalUtils.getUserId());
        articleMapper.insert(article);
    }

    @Override
    public void update(Article article) {
        article.setUserId(ThreadLocalUtils.getUserId());
        articleMapper.update(article);
    }

    @Override
    public Article getById(Long id) {
        Article article = articleMapper.findById(id);
        if (article != null) {
            articleMapper.incrementViewCount(id);
        }
        return article;
    }

    @Override
    public void delete(Long id) {
        articleMapper.deleteById(id, ThreadLocalUtils.getUserId());
    }

    @Override
    public List<Article> list(Long categoryId, String state) {
        Long userId = ThreadLocalUtils.getUserId();
        Integer stateInt = null;
        if ("草稿".equals(state)) {
            stateInt = 0;
        } else if ("已发布".equals(state)) {
            stateInt = 1;
        }
        return articleMapper.findByCondition(userId, categoryId, stateInt);
    }
}
