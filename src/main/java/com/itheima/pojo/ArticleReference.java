package com.itheima.pojo;

import java.time.LocalDateTime;

/** Article metadata that actually came back from a tool invocation. */
public record ArticleReference(
        Long id,
        String title,
        String summary,
        String categoryName,
        String authorNickname,
        LocalDateTime publishedAt) {
}
