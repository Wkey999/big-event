package com.itheima.pojo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FeedRun {
    private Long id;
    private String sourceKey;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int scanned;
    private int inserted;
    private int duplicates;
    private int filtered;
    private boolean success;
    private boolean skipped;
    private int autoPublished;
    private int autoPending;
    private String message;
}
