package com.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/** Narrow article metadata returned to read-only Agent tools (no article body). */
@Data
public class ArticleSearchRow {
    private Long id;
    private String title;
    private String summary;
    private String coverImg;
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Long categoryId;
    private String categoryName;
    private String authorNickname;
    private String authorAvatar;
    private LocalDateTime publishedAt;
    private String hitField;
    private String hitSnippet;
}
