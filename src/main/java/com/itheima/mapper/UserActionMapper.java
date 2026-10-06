package com.itheima.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface UserActionMapper {

    /**
     * 查询是否已存在该互动关系。两个用途：判幂等、详情接口回填 liked/collected。
     * 只取 id（命中唯一索引 uk_user_target_action），不做全行查询。
     */
    @Select("SELECT id FROM user_action WHERE user_id = #{userId} AND target_id = #{targetId} " +
            "AND target_type = #{targetType} AND action_type = #{actionType} LIMIT 1")
    Long findId(@Param("userId") Long userId,
                @Param("targetId") Long targetId,
                @Param("targetType") Integer targetType,
                @Param("actionType") Integer actionType);

    @Insert("INSERT INTO user_action(user_id, target_id, target_type, action_type) " +
            "VALUES(#{userId}, #{targetId}, #{targetType}, #{actionType})")
    int insert(@Param("userId") Long userId,
               @Param("targetId") Long targetId,
               @Param("targetType") Integer targetType,
               @Param("actionType") Integer actionType);

    /**
     * 取消互动：关系表没有软删除列（它是纯关系，不是业务实体），取消就是物理删除。
     * 返回影响行数用于幂等判断：0 行说明本来就没这条关系。
     */
    @Delete("DELETE FROM user_action WHERE user_id = #{userId} AND target_id = #{targetId} " +
            "AND target_type = #{targetType} AND action_type = #{actionType}")
    int delete(@Param("userId") Long userId,
               @Param("targetId") Long targetId,
               @Param("targetType") Integer targetType,
               @Param("actionType") Integer actionType);
}
