package com.itheima.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FollowMapper {

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int countRelation(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Insert("INSERT INTO user_follow(follower_id, followee_id) VALUES(#{followerId}, #{followeeId})")
    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Delete("DELETE FROM user_follow WHERE follower_id = #{followerId} AND followee_id = #{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE followee_id = #{userId}")
    long countFollowers(Long userId);

    @Select("SELECT COUNT(*) FROM user_follow WHERE follower_id = #{userId}")
    long countFollowees(Long userId);
}
