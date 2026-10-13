package com.itheima.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentDTO {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容最长500个字符")
    private String content;

    private Long rootId;
    private Long parentId;
}
