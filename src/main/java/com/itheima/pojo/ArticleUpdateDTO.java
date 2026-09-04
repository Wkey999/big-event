package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新文章请求参数
 *
 * 之前 controller 直接收 Article 实体，既没有校验，也把 userId/viewCount 这类
 * 不该由客户端决定的字段暴露出来。这里只收前端能改的字段，id 必填。
 * 只用于更新，所以不需要 ValidationGroups 分组，@Valid 即可。
 */
@Data
public class ArticleUpdateDTO {
    /** 文章ID：必填 */
    @NotNull(message = "文章ID不能为空")
    private Long id;

    /** 标题：必填，最长50字 */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 50, message = "标题最长50个字符")
    private String title;

    /** 正文：必填 */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /** 封面图URL：可选，列是 NOT NULL DEFAULT ''，为 null 时由 service 兜底成空串 */
    private String coverImg;

    /** 摘要：可选，最长200字 */
    @Size(max = 200, message = "摘要最长200个字符")
    private String summary;

    /** 所属分类：必填 */
    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    /** 状态：必填，且只能是0(草稿)或1(已发布) */
    @NotNull(message = "文章状态不能为空")
    @InValues(values = {0, 1}, message = "文章状态只能是0-草稿或1-已发布")
    private Integer state;
}
