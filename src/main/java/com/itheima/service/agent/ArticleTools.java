package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.BrowseLogDTO;
import com.itheima.pojo.ArticleQueryResult;
import com.itheima.pojo.ArticleReference;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.Category;
import com.itheima.service.BrowseLogService;
import com.itheima.utils.LikeUtils;
import com.itheima.utils.SearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Read-only search tools; the mapper enforces published/non-deleted visibility independently of the prompt. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleTools {

    private static final int MAX_RESULT_LIMIT = 20;
    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final BrowseLogService browseLogService;

    @Tool(name = "query_published_articles", description = "Search the community's published articles by keyword and optional category. Drafts are never returned.")
    public ArticleQueryResult queryArticles(
            @ToolParam(description = "Keyword or short phrase to search for; use an empty string for recent published articles") String keyword,
            @ToolParam(description = "Optional exact category name", required = false) String category,
            @ToolParam(description = "Maximum number of results, from 1 to 20", required = false) Integer limit,
            @ToolParam(description = "Publication time window: all, 1d, 7d, or 30d", required = false) String timeRange,
            @ToolParam(description = "Ordering: latest or popular", required = false) String orderBy) {

        String boundedKeyword = bound(keyword, MAX_KEYWORD_LENGTH);
        String boundedCategory = bound(category, 20);
        Long categoryId = null;
        if (!boundedCategory.isEmpty()) {
            Category match = categoryMapper.findByName(boundedCategory);
            if (match == null) {
                return new ArticleQueryResult(List.of(), false);
            }
            categoryId = match.getId();
        }

        int resultLimit = limit == null ? DEFAULT_RESULT_LIMIT : Math.max(1, Math.min(limit, MAX_RESULT_LIMIT));
        String normalizedRange = timeRange == null || timeRange.isBlank()
                ? "all"
                : timeRange.trim().toLowerCase(Locale.ROOT);
        String normalizedOrder = SearchFilters.orderBy(orderBy);
        List<ArticleSearchRow> rows = articleMapper.searchPublished(
                boundedKeyword.isEmpty() ? null : LikeUtils.escape(boundedKeyword),
                categoryId,
                null,
                SearchFilters.createdAfter(normalizedRange, java.time.LocalDateTime.now()),
                normalizedOrder,
                0,
                resultLimit + 1);
        boolean truncated = rows.size() > resultLimit;
        List<ArticleReference> references = rows.stream()
                .limit(resultLimit)
                .map(ArticleTools::toReference)
                .toList();
        AskReferenceContext.addAll(references);
        for (ArticleReference reference : references) {
            BrowseLogDTO exposure = new BrowseLogDTO();
            exposure.setArticleId(reference.id());
            exposure.setDwellMs(0);
            exposure.setMode("ask");
            try {
                // The references rendered with an Agent answer count as ask-mode impressions.
                browseLogService.report(exposure);
            } catch (Exception e) {
                log.warn("Agent 引用曝光埋点失败，异常类型={}", e.getClass().getSimpleName());
            }
        }
        return new ArticleQueryResult(references, truncated);
    }

    private static String bound(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static ArticleReference toReference(ArticleSearchRow row) {
        String title=row.getSourceTranslatedTitle()!=null?row.getSourceTranslatedTitle():(row.getSourceTitle()==null?row.getTitle():row.getSourceTitle());
        return new ArticleReference(row.getId(), title, row.getSummary(), row.getCategoryName(),
                row.getAuthorNickname(), row.getPublishedAt(), row.getSourceName(), row.getSourceUrl(), row.getSourceAuthor());
    }
}
