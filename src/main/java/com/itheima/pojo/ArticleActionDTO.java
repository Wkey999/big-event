package com.itheima.pojo;

import com.itheima.validation.InValues;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章互动参数（点赞 / 收藏）
 *
 * actionType 用数值而不是 "like"/"collect" 字符串：与 user_action.action_type 的取值一一对应，
 * 复用项目已有的 @InValues 自定义校验注解，少一层字符串映射。
 */
@Data
public class ArticleActionDTO {
    /** 文章ID */
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    /** 互动类型：1-点赞 2-收藏 */
    @NotNull(message = "互动类型不能为空")
    @InValues(values = {1, 2}, message = "互动类型只能是1-点赞或2-收藏")
    private Integer actionType;

    /** 操作类型：1-执行 0-取消 */
    @NotNull(message = "操作类型不能为空")
    @InValues(values = {0, 1}, message = "操作类型只能是1-执行或0-取消")
    private Integer op;
}
