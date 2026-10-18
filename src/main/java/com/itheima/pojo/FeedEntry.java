package com.itheima.pojo;
import lombok.Data;
import java.time.LocalDateTime;

/** Metadata only: descriptions, article bodies and images are not persisted. */
@Data
public class FeedEntry {
    private Long id;
    private String sourceKey;
    private String sourceName;
    private String sourceUrl;
    private String urlHash;
    private String title;
    private String translatedTitle;
    private String sourceAuthor;
    private LocalDateTime sourcePublishedAt;
    private LocalDateTime fetchedAt;
    private Integer status;
    private Long articleId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
}
