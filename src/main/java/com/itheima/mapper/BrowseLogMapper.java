package com.itheima.mapper;

import com.itheima.pojo.CategoryStat;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BrowseLogMapper {

    /**
     * 去抖查询：同一用户对同一文章在 since 之后是否已有记录。
     * 只取 id，不查整行——埋点是写入最频繁的端点，少一次无用列传输。
     * 走 idx_user_time(user_id, create_time) 索引。
     */
    @Select("SELECT id FROM browse_log WHERE user_id = #{userId} AND article_id = #{articleId} " +
            "AND `mode` = #{mode} AND create_time >= #{since} ORDER BY id DESC LIMIT 1")
    Long findRecentId(@Param("userId") Long userId,
                      @Param("articleId") Long articleId,
                      @Param("mode") String mode,
                      @Param("since") LocalDateTime since);

    /**
     * 去抖命中：只把停留时长往大里更新。
     * 曝光(0)先到、关闭预览后真实时长再到，取大值避免后到的小值把时长改小。
     */
    @Update("UPDATE browse_log SET dwell_ms = GREATEST(dwell_ms, #{dwellMs}) WHERE id = #{id}")
    int updateDwell(@Param("id") Long id, @Param("dwellMs") Integer dwellMs);

    @Insert("INSERT INTO browse_log(user_id, article_id, category_id, dwell_ms, `mode`) " +
            "VALUES(#{userId}, #{articleId}, #{categoryId}, #{dwellMs}, #{mode})")
    int insert(@Param("userId") Long userId,
               @Param("articleId") Long articleId,
               @Param("categoryId") Long categoryId,
               @Param("dwellMs") Integer dwellMs,
               @Param("mode") String mode);

    // ---- 阶段 B' 规则画像的聚合查询 ----

    /**
     * 按频道聚合近 N 天浏览。未分类文章（category_id 为 NULL）不参与画像：
     * 它归不到任何频道，混进来只会稀释权重。
     */
    @Select("SELECT l.category_id AS categoryId, c.category_name AS categoryName, " +
            "COUNT(*) AS pv, ROUND(AVG(l.dwell_ms)) AS avgDwellMs " +
            "FROM browse_log l LEFT JOIN category c ON c.id = l.category_id " +
            "WHERE l.user_id = #{userId} AND l.create_time >= #{since} AND l.category_id IS NOT NULL " +
            "GROUP BY l.category_id, c.category_name")
    List<CategoryStat> statsByCategory(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 活跃时段：按小时分组，取出现次数最多的前 limit 个（平票按小时升序） */
    @Select("SELECT HOUR(create_time) FROM browse_log WHERE user_id = #{userId} AND create_time >= #{since} " +
            "GROUP BY HOUR(create_time) ORDER BY COUNT(*) DESC, HOUR(create_time) ASC LIMIT #{limit}")
    List<Integer> activeHours(@Param("userId") Long userId,
                              @Param("since") LocalDateTime since,
                              @Param("limit") int limit);

    /** 定时任务用：近 N 天有过行为的用户（只给活跃用户生成画像，不做全表扫描） */
    @Select("SELECT DISTINCT user_id FROM browse_log WHERE create_time >= #{since}")
    List<Long> activeUserIds(@Param("since") LocalDateTime since);
}
