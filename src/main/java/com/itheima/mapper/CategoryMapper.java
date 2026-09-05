package com.itheima.mapper;

import com.itheima.pojo.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("SELECT * FROM category WHERE user_id = #{userId} AND deleted = 0 ORDER BY sort_order, create_time")
    List<Category> findByUserId(Long userId);

    // 全站共享分类：任何人可看、可引用（做文章分类下拉/标签映射）
    @Select("SELECT * FROM category WHERE deleted = 0 ORDER BY sort_order, create_time")
    List<Category> findAll();

    // 全站范围内分类名唯一；新模型下分类是共享池，重名要跨用户检查
    @Select("SELECT * FROM category WHERE category_name = #{categoryName} AND deleted = 0 ORDER BY id LIMIT 1")
    Category findByName(@Param("categoryName") String categoryName);

    // sort_order/status 在 DTO 里是可选字段，但列是 NOT NULL，缺省时用列默认值 0/1
    @Insert("INSERT INTO category(category_name, user_id, sort_order, status) " +
            "VALUES(#{categoryName}, #{userId}, " +
            "IFNULL(#{sortOrder,jdbcType=INTEGER}, 0), IFNULL(#{status,jdbcType=INTEGER}, 1))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Select("SELECT * FROM category WHERE id = #{id} AND deleted = 0")
    Category findById(Long id);

    // 传 null 表示该字段不改，保留原值（列是 NOT NULL，不能直接写 null）
    @Update("UPDATE category SET category_name = #{categoryName}, " +
            "sort_order = IFNULL(#{sortOrder,jdbcType=INTEGER}, sort_order), " +
            "status = IFNULL(#{status,jdbcType=INTEGER}, status), " +
            "update_time = NOW() WHERE id = #{id}")
    int update(Category category);

    @Update("UPDATE category SET deleted = 1, update_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}
