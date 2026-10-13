package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentView {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private String content;
    private Integer likeCount;
    private Boolean liked;
    private Integer replyCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private List<CommentReplyView> replies;
}
