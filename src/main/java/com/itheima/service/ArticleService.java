package com.itheima.service;

import com.itheima.pojo.Article;

import java.util.List;

public interface ArticleService {

    void add(Article article);

    void update(Article article);

    Article getById(Long id);

    void delete(Long id);

    List<Article> list(Long categoryId, String state);
}
