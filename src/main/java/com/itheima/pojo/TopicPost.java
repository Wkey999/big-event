package com.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/** Published post card fields; deliberately excludes the article body. */
@Data
public class TopicPost {
    private Long id;
    private String title;
    private String coverImg;
    private String summary;
    private Long userId;
    private Long categoryId;
    private Integer state;
    private Integer viewCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String authorNickname;
    private String authorAvatar;
}
