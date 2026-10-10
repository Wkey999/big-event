package com.itheima.mapper;

import com.itheima.pojo.CategoryStat;
import com.itheima.pojo.ProfileInterest;
import com.itheima.pojo.UserProfile;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserProfileMapper {

    /**
     * 画像主表 upsert：一人一行，重新生成就是覆盖。
     * ON DUPLICATE KEY UPDATE 让「首次生成」和「重新生成」走同一条 SQL，省掉一次查询。
     */
    @Insert("INSERT INTO user_profile(user_id, active_hours, source, sample_size, generated_at) " +
            "VALUES(#{userId}, #{activeHours}, #{source}, #{sampleSize}, NOW()) " +
            "ON DUPLICATE KEY UPDATE active_hours = VALUES(active_hours), source = VALUES(source), " +
            "sample_size = VALUES(sample_size), generated_at = NOW()")
    int upsertProfile(UserProfile profile);

    @Select("SELECT user_id AS userId, active_hours AS activeHours, source, sample_size AS sampleSize, generated_at AS generatedAt " +
            "FROM user_profile WHERE user_id = #{userId}")
    UserProfile findProfile(Long userId);

    @Select("SELECT user_id AS userId, category_id AS categoryId, category_name AS categoryName, " +
            "weight, pv, avg_dwell_ms AS avgDwellMs, actions " +
            "FROM user_profile_interest WHERE user_id = #{userId} ORDER BY weight DESC, category_id ASC")
    List<ProfileInterest> findInterests(Long userId);

    /** 重新生成时先清掉旧权重行，避免已不再关注的频道残留 */
    @Delete("DELETE FROM user_profile_interest WHERE user_id = #{userId}")
    int deleteInterests(Long userId);

    @Delete("DELETE FROM user_profile WHERE user_id = #{userId}")
    int deleteProfile(Long userId);

    /** 批量插入权重明细（画像行数 = 关注的频道数，通常个位数，但仍用批量省往返） */
    @Insert("<script>" +
            "INSERT INTO user_profile_interest(user_id, category_id, category_name, weight, pv, avg_dwell_ms, actions) VALUES " +
            "<foreach collection='list' item='it' separator=','>" +
            "(#{it.userId}, #{it.categoryId}, #{it.categoryName}, #{it.weight}, #{it.pv}, #{it.avgDwellMs}, #{it.actions})" +
            "</foreach>" +
            "</script>")
    int insertInterests(@Param("list") List<ProfileInterest> list);
}
