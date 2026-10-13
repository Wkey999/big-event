package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentReplyView {
    private Long id;
    private Long parentId;
    private String content;
    private String nickname;
    private Integer likeCount;
    private Boolean liked;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
