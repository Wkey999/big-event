package com.itheima.service;

import com.itheima.pojo.AdminArticleUpdateDTO;
import com.itheima.pojo.Article;
import com.itheima.pojo.PageBean;

public interface AdminArticleService {
    PageBean<Article> list(Integer pageNum, Integer pageSize, Integer state, Long authorId,
                           Long categoryId, String keyword);

    Article detail(Long id);

    void update(Long id, AdminArticleUpdateDTO dto);

    void delete(Long id);

    void updateState(Long id, Integer state);
}
