package com.itheima.service;

import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.PageBean;

public interface ArticleSearchService {
    PageBean<ArticleSearchRow> search(String keyword, Integer pageNum, Integer pageSize,
                                      Long categoryId, Long authorId, String timeRange, String orderBy);
}
