package com.itheima.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostMedia {
    private Long id;
    @JsonIgnore
    private Long articleId;
    private String url;
    private Integer mediaType;
    private Integer sortOrder;
    private Integer width;
    private Integer height;
    @JsonIgnore
    private LocalDateTime createTime;
}
