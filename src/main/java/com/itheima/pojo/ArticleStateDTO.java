package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArticleStateDTO {
    @NotNull(message = "文章状态不能为空")
    @InValues(values = {0, 1}, message = "文章状态只能是0-草稿或1-已发布")
    private Integer state;
}
