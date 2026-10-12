package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin update body intentionally omits id/author fields; id is taken from the path. */
@Data
public class AdminArticleUpdateDTO {
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 50, message = "标题最长50个字符")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    private String coverImg;

    @Size(max = 200, message = "摘要最长200个字符")
    private String summary;

    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    @NotNull(message = "文章状态不能为空")
    @InValues(values = {0, 1}, message = "文章状态只能是0-草稿或1-已发布")
    private Integer state;
}
