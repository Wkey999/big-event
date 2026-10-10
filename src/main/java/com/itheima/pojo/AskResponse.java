package com.itheima.pojo;

import java.util.List;

/** The answer plus references assembled from actual tool results, never from model-generated IDs. */
public record AskResponse(String answer, List<ArticleReference> references) {
}
