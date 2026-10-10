package com.itheima.service;

import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleAddDTO;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.PageBean;

public interface ArticleService {

    void add(ArticleAddDTO dto);

    void update(ArticleUpdateDTO dto);

    Article getById(Long id);

    /**
     * 编辑预填：返回完整文章（含 content），且不增加浏览量。
     * 列表接口已收窄列不返回正文，编辑弹窗靠这个接口回填；
     * 权限与写操作一致（作者本人或管理员）。
     */
    Article getForEdit(Long id);

    void delete(Long id);

    PageBean<Article> list(Integer pageNum, Integer pageSize, Long categoryId, String state, String mode);
}
