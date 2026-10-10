package com.itheima.service.agent;

import com.itheima.mapper.ArticleMapper;
import com.itheima.mapper.CategoryMapper;
import com.itheima.pojo.ArticleQueryResult;
import com.itheima.pojo.ArticleReference;
import com.itheima.pojo.ArticleSearchRow;
import com.itheima.pojo.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only search tools; the mapper enforces published/non-deleted visibility independently of the prompt. */
@Component
@RequiredArgsConstructor
public class ArticleTools {

    private static final int MAX_RESULT_LIMIT = 20;
    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;

    @Tool(name = "query_published_articles", description = "Search the community's published articles by keyword and optional category. Drafts are never returned.")
    public ArticleQueryResult queryArticles(
            @ToolParam(description = "Keyword or short phrase to search for; use an empty string for recent published articles") String keyword,
            @ToolParam(description = "Optional exact category name", required = false) String category,
            @ToolParam(description = "Maximum number of results, from 1 to 20", required = false) Integer limit) {

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
        List<ArticleSearchRow> rows = articleMapper.searchPublished(
                boundedKeyword.isEmpty() ? null : boundedKeyword,
                categoryId,
                resultLimit + 1);
        boolean truncated = rows.size() > resultLimit;
        List<ArticleReference> references = rows.stream()
                .limit(resultLimit)
                .map(ArticleTools::toReference)
                .toList();
        AskReferenceContext.addAll(references);
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
        return new ArticleReference(row.getId(), row.getTitle(), row.getSummary(), row.getCategoryName(),
                row.getAuthorNickname(), row.getPublishedAt());
    }
}
