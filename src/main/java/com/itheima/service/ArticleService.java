package com.itheima.service;

import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.PageBean;

public interface ArticleService {

    void add(ArticleAddDTO dto);

    void update(ArticleUpdateDTO dto);

    Article getById(Long id);

    void delete(Long id);

    PageBean<Article> list(Integer pageNum, Integer pageSize, Long categoryId, String state);
}
