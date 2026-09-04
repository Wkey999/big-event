package com.itheima.pojo;

import com.itheima.validation.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 分类新增/更新请求参数
 * 用校验分组区分：新增时不校验id，更新时id必填
 */
@Data
public class CategoryDTO {
    /** 分类ID：只有更新时必填（Update分组） */
    @NotNull(message = "分类ID不能为空", groups = ValidationGroups.Update.class)
    private Long id;

    /** 分类名称：新增和更新都必填，1-20位 */
    @NotBlank(message = "分类名称不能为空")
    @Pattern(regexp = "^\\S{1,20}$", message = "分类名称必须是1-20位非空字符")
    private String categoryName;

    /** 排序权重：可选，值越小越靠前 */
    private Integer sortOrder;

    /** 状态：可选，0-禁用 1-启用 */
    private Integer status;
}
