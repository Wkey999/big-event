package com.itheima.pojo;

import lombok.Data;

/**
 * 频道维度的行为聚合结果（阶段 B' 规则画像的原料）
 * 两个来源各填各的字段：浏览统计填 pv/avgDwellMs，互动统计填 actions，最后按 categoryId 合并。
 */
@Data
public class CategoryStat {
    /** 频道ID */
    private Long categoryId;
    /** 频道名（LEFT JOIN 来，文章可能未分类所以可能为空） */
    private String categoryName;
    /** 近 7 天浏览量 */
    private Integer pv;
    /** 平均停留毫秒 */
    private Integer avgDwellMs;
    /** 近 7 天点赞+收藏数 */
    private Integer actions;
}
