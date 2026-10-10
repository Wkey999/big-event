package com.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/** Narrow article metadata returned to read-only Agent tools (no article body). */
@Data
public class ArticleSearchRow {
    private Long id;
    private String title;
    private String summary;
    private Long categoryId;
    private String categoryName;
    private String authorNickname;
    private LocalDateTime publishedAt;
}
