package com.itheima.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 浏览/曝光上报参数（阶段 A 行为采集）
 *
 * userId 由 token 决定、categoryId 从文章行读取，都不接受客户端传入；mode 仅作为受限枚举用于统计归因。
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

    /** 曝光来源；旧客户端缺省时由 service 记为 latest */
    @Pattern(regexp = "latest|recommend", message = "信息流模式仅支持 latest 或 recommend")
    private String mode;
}
