package com.itheima.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchLogMapper {

    @Insert("INSERT INTO search_log(user_id, keyword, category_id, time_range, result_count, elapsed_ms) " +
            "VALUES(#{userId}, #{keyword}, #{categoryId}, #{timeRange}, #{resultCount}, #{elapsedMs})")
    int insert(@Param("userId") Long userId,
               @Param("keyword") String keyword,
               @Param("categoryId") Long categoryId,
               @Param("timeRange") String timeRange,
               @Param("resultCount") int resultCount,
               @Param("elapsedMs") int elapsedMs);
}
