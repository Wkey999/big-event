package com.itheima.mapper;

import com.itheima.pojo.Article;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Insert("INSERT INTO article(title, content, cover_img, summary, user_id, category_id, state) " +
            "VALUES(#{title}, #{content}, #{coverImg}, #{summary}, #{userId}, #{categoryId}, #{state})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Article article);

    @Update("UPDATE article SET title = #{title}, content = #{content}, cover_img = #{coverImg}, " +
            "summary = #{summary}, category_id = #{categoryId}, state = #{state}, update_time = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId}")
    int update(Article article);

    @Select("SELECT * FROM article WHERE id = #{id} AND deleted = 0")
    Article findById(Long id);

    @Update("UPDATE article SET deleted = 1, update_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    List<Article> findByCondition(@Param("userId") Long userId,
                                  @Param("categoryId") Long categoryId,
                                  @Param("state") Integer state);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(Long id);
}
