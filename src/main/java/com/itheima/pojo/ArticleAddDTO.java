package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增文章请求参数 - 带参数校验和自定义校验
 */
@Data
public class ArticleAddDTO {
    /** 标题：必填，最长50字 */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 50, message = "标题最长50个字符")
    private String title;

    /** 正文：必填 */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /** 封面图URL：可选 */
    private String coverImg;

    /** 摘要：可选，最长200字 */
    @Size(max = 200, message = "摘要最长200个字符")
    private String summary;

    /** 所属分类：必填 */
    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    /** 状态：必填，且只能是0(草稿)或1(已发布) —— 自定义校验注解 */
    @NotNull(message = "文章状态不能为空")
    @InValues(values = {0, 1}, message = "文章状态只能是0-草稿或1-已发布")
    private Integer state;

    /** Optional community media; null preserves legacy cover_img behavior. */
    @Size(max = 10, message = "媒体最多10项")
    private List<@jakarta.validation.Valid PostMediaDTO> media;

    /** Optional topic set; null means no topics were supplied. */
    @Size(max = 5, message = "一篇帖子最多添加5个话题")
    private List<String> topicNames;
}
