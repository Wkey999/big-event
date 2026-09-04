package com.itheima.mapper;

import com.itheima.pojo.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("SELECT * FROM category WHERE user_id = #{userId} AND deleted = 0 ORDER BY sort_order, create_time")
    List<Category> findByUserId(Long userId);

    @Insert("INSERT INTO category(category_name, user_id) VALUES(#{categoryName}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Select("SELECT * FROM category WHERE id = #{id} AND deleted = 0")
    Category findById(Long id);

    @Update("UPDATE category SET category_name = #{categoryName}, update_time = NOW() WHERE id = #{id}")
    int update(Category category);

    @Update("UPDATE category SET deleted = 1, update_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
