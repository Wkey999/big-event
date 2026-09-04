package com.itheima.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文章分类实体类 - 对应数据库 category 表
 */
@Data
public class Category {
    /** 主键ID */
    private Long id;
    /** 分类名称 */
    private String categoryName;
    /** 所属用户ID */
    private Long userId;
    /** 排序权重（越小越靠前） */
    private Integer sortOrder;
    /** 状态：0-禁用 1-启用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除：0-未删 1-已删 */
    private Integer deleted;
}
