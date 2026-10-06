package com.itheima.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface BrowseLogMapper {

    /**
     * 去抖查询：同一用户对同一文章在 since 之后是否已有记录。
     * 只取 id，不查整行——埋点是写入最频繁的端点，少一次无用列传输。
     * 走 idx_user_time(user_id, create_time) 索引。
     */
    @Select("SELECT id FROM browse_log WHERE user_id = #{userId} AND article_id = #{articleId} " +
            "AND create_time >= #{since} ORDER BY id DESC LIMIT 1")
    Long findRecentId(@Param("userId") Long userId,
                      @Param("articleId") Long articleId,
                      @Param("since") LocalDateTime since);

    /**
     * 去抖命中：只把停留时长往大里更新。
     * 曝光(0)先到、关闭预览后真实时长再到，取大值避免后到的小值把时长改小。
     */
    @Update("UPDATE browse_log SET dwell_ms = GREATEST(dwell_ms, #{dwellMs}) WHERE id = #{id}")
    int updateDwell(@Param("id") Long id, @Param("dwellMs") Integer dwellMs);

    @Insert("INSERT INTO browse_log(user_id, article_id, category_id, dwell_ms) " +
            "VALUES(#{userId}, #{articleId}, #{categoryId}, #{dwellMs})")
    int insert(@Param("userId") Long userId,
               @Param("articleId") Long articleId,
               @Param("categoryId") Long categoryId,
               @Param("dwellMs") Integer dwellMs);
}
