package com.itheima.pojo;

import java.util.List;

/** Structured result passed back to the model by the bounded article-search tool. */
public record ArticleQueryResult(List<ArticleReference> items, boolean truncated) {
}
