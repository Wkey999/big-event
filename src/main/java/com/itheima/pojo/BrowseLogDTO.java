package com.itheima.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 浏览/曝光上报参数（阶段 A 行为采集）
 *
 * 只信这两个字段：userId 由 token 决定、categoryId 从文章行读取，
 * 都不接受客户端传入——埋点是推荐系统的信号源，被客户端随意指定就失去意义了。
 */
@Data
public class BrowseLogDTO {
    /** 文章ID */
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    /** 停留毫秒：0 表示仅曝光（卡片进入视口），上限 5 分钟防前端传脏值 */
    @Min(value = 0, message = "停留时长不合法")
    @Max(value = 300000, message = "停留时长不合法")
    private Integer dwellMs;
}
