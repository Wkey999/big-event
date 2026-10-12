package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.AdminMapper;
import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.AdminArticleUpdateDTO;
import com.itheima.pojo.AdminArticleListItem;
import com.itheima.pojo.Article;
import com.itheima.pojo.ArticleUpdateDTO;
import com.itheima.pojo.PageBean;
import com.itheima.service.AdminArticleService;
import com.itheima.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AdminArticleServiceImpl implements AdminArticleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminMapper adminMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;

    @Override
    public PageBean<Article> list(Integer pageNum, Integer pageSize, Integer state, Long authorId,
                                  Long categoryId, String keyword) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        if (state != null && state != 0 && state != 1) {
            throw new BusinessException("文章状态只能是0或1");
        }
        String safeKeyword = keyword == null ? null : keyword.trim();
        if (safeKeyword != null && safeKeyword.length() > 100) {
            safeKeyword = safeKeyword.substring(0, 100);
        }
        Long total = adminMapper.countAdminArticles(state, authorId, categoryId, safeKeyword);
        List<AdminArticleListItem> rows = total == null || total == 0
                ? List.of()
                : adminMapper.findAdminArticles(state, authorId, categoryId, safeKeyword,
                        Math.multiplyExact(safePage - 1, safeSize), safeSize);
        List<Article> items = new ArrayList<>(rows);
        return new PageBean<>(total == null ? 0L : total, items);
    }

    @Override
    public Article detail(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        return article;
    }

    @Override
    public void update(Long id, AdminArticleUpdateDTO dto) {
        ArticleUpdateDTO update = new ArticleUpdateDTO();
        update.setId(id);
        update.setTitle(dto.getTitle());
        update.setContent(dto.getContent());
        update.setCoverImg(dto.getCoverImg());
        update.setSummary(dto.getSummary());
        update.setCategoryId(dto.getCategoryId());
        update.setState(dto.getState());
        // Reuse the existing author/admin SQL enforcement and category lock; original author is preserved.
        articleService.update(update);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Already-deleted and unknown IDs share a successful idempotent delete response.
        Article active = articleMapper.findById(id);
        if (active != null) {
            articleMapper.deleteByIdAny(id);
        }
    }

    @Override
    @Transactional
    public void updateState(Long id, Integer state) {
        if (articleMapper.findById(id) == null) {
            throw new BusinessException("文章不存在");
        }
        articleMapper.updateStateAny(id, state);
    }
}
