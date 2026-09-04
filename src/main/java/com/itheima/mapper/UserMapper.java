package com.itheima.mapper;

import com.itheima.pojo.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE username = #{username} AND deleted = 0")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, nickname) VALUES(#{username}, #{password}, #{nickname})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM user WHERE id = #{id} AND deleted = 0")
    User findById(Long id);

    @Update("<script>" +
            "UPDATE user SET update_time = NOW()" +
            "<if test='nickname != null'>, nickname = #{nickname}</if>" +
            "<if test='email != null'>, email = #{email}</if>" +
            "<if test='avatar != null'>, avatar = #{avatar}</if>" +
            " WHERE id = #{id}" +
            "</script>")
    int update(User user);

    @Update("UPDATE user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
