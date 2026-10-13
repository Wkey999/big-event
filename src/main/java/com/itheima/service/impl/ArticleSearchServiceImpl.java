package com.itheima.service.impl;

import com.itheima.mapper.ArticleMapper;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.PageBean;
import com.itheima.service.ArticleSearchService;
import com.itheima.service.SearchLogService;
import com.itheima.utils.LikeUtils;
import com.itheima.utils.SearchFilters;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ArticleSearchServiceImpl implements ArticleSearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArticleMapper articleMapper;
    private final SearchLogService searchLogService;

    @Override
    public PageBean<ArticleSearchRow> search(String keyword, Integer pageNum, Integer pageSize,
                                             Long categoryId, Long authorId, String timeRange, String orderBy) {
        String normalizedKeyword = keyword.trim();
        String sqlKeyword = LikeUtils.escape(normalizedKeyword);
        String normalizedRange = timeRange == null || timeRange.isBlank()
                ? "all"
                : timeRange.trim().toLowerCase(Locale.ROOT);
        String normalizedOrder = SearchFilters.orderBy(orderBy);
        LocalDateTime createdAfter = SearchFilters.createdAfter(normalizedRange, LocalDateTime.now());
        int safePage = pageNum == null || pageNum < 1 ? 1 : Math.min(pageNum, 100_000);
        int safeSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        long offsetLong = Math.max(0L, (long) (safePage - 1) * safeSize);
        int offset = (int) Math.min(offsetLong, Integer.MAX_VALUE);
        long startedAt = System.nanoTime();
        Long total = articleMapper.countPublishedSearch(sqlKeyword, categoryId, authorId, createdAfter);
        List<ArticleSearchRow> rows = total == null || total == 0
                ? List.of()
                : articleMapper.searchPublished(sqlKeyword, categoryId, authorId, createdAfter,
                        normalizedOrder, offset, safeSize);
        rows.forEach(row -> addHitMetadata(row, normalizedKeyword));
        Long userId = ThreadLocalUtils.getUserId();
        searchLogService.record(userId, normalizedKeyword, categoryId, normalizedRange,
                total == null ? 0 : total, (System.nanoTime() - startedAt) / 1_000_000L);
        return new PageBean<>(total == null ? 0L : total, rows);
    }

    private static void addHitMetadata(ArticleSearchRow row, String keyword) {
        String title = plainText(row.getTitle());
        String summary = plainText(row.getSummary());
        String titleLower = title.toLowerCase(Locale.ROOT);
        String keywordLower = keyword.toLowerCase(Locale.ROOT);
        if (titleLower.contains(keywordLower)) {
            row.setHitField("title");
            row.setHitSnippet(snippet(title, titleLower.indexOf(keywordLower), keyword.length()));
            return;
        }
        String summaryLower = summary.toLowerCase(Locale.ROOT);
        if (summaryLower.contains(keywordLower)) {
            row.setHitField("summary");
            row.setHitSnippet(snippet(summary, summaryLower.indexOf(keywordLower), keyword.length()));
            return;
        }
        row.setHitField(null);
        row.setHitSnippet(snippet(summary, 0, 0));
    }

    private static String plainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HtmlUtils.htmlUnescape(value.replaceAll("(?s)<[^>]*>", " "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String snippet(String text, int matchStart, int matchLength) {
        if (text.length() <= 60) {
            return text;
        }
        int start = Math.max(0, matchStart < 0 ? 0 : matchStart - Math.max(0, (58 - matchLength) / 2));
        int end = Math.min(text.length(), start + 58);
        if (end - start < 58) {
            start = Math.max(0, end - 58);
        }
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }
}
