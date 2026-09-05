package com.itheima.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文章实体类 - 对应数据库 article 表
 */
@Data
public class Article {
    /** 主键ID */
    private Long id;
    /** 文章标题 */
    private String title;
    /** 文章内容（富文本HTML） */
    private String content;
    /** 封面图URL */
    private String coverImg;
    /** 文章摘要（列表页展示用） */
    private String summary;
    /** 作者ID */
    private Long userId;
    /** 所属分类ID */
    private Long categoryId;
    /** 状态：0-草稿 1-已发布 */
    private Integer state;
    /** 浏览次数 */
    private Integer viewCount;
    /** 点赞次数（冗余计数） */
    private Integer likeCount;
    /** 评论次数（冗余计数） */
    private Integer commentCount;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除：0-未删 1-已删 */
    private Integer deleted;

    /** 作者昵称 —— 非 article 表字段，仅列表查询 LEFT JOIN user 时填充（信息流卡片展示用） */
    private String authorNickname;
    /** 作者头像 —— 同上，非表字段 */
    private String authorAvatar;
}
