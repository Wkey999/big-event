package com.itheima.pojo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 画像里的一条频道兴趣（对应 user_profile_interest 一行）
 */
@Data
public class ProfileInterest {
    /** 用户ID（批量插入时需要） */
    private Long userId;
    /** 频道ID */
    private Long categoryId;
    /** 频道名快照：存下来展示时免 JOIN，也让画像成为自包含的历史快照 */
    private String categoryName;
    /** 归一化权重 0~1，同一用户所有频道之和 ≈ 1 */
    private BigDecimal weight;
    /** 近 7 天浏览量 */
    private Integer pv;
    /** 平均停留毫秒 */
    private Integer avgDwellMs;
    /** 近 7 天点赞+收藏数 */
    private Integer actions;
}
