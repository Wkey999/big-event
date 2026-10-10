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
            "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int update(Article article);

    // 管理员更新任意文章：不限制作者，且不改动 user_id（保留原作者）
    @Update("UPDATE article SET title = #{title}, content = #{content}, cover_img = #{coverImg}, " +
            "summary = #{summary}, category_id = #{categoryId}, state = #{state}, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int updateAny(Article article);

    @Select("SELECT * FROM article WHERE id = #{id} AND deleted = 0")
    Article findById(Long id);

    @Update("UPDATE article SET deleted = 1, update_time = NOW() WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    // 管理员删除任意文章：不限制作者
    @Update("UPDATE article SET deleted = 1, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int deleteByIdAny(Long id);

    // 类别删除事务中的当前读：配合 category 行锁，确保能看到事务开始后新提交的文章。
    @Select("SELECT count(*) FROM article WHERE category_id = #{categoryId} AND deleted = 0 LOCK IN SHARE MODE")
    Long countByCategoryId(Long categoryId);

    List<Article> findByCondition(@Param("userId") Long userId,
                                  @Param("categoryId") Long categoryId,
                                  @Param("state") Integer state,
                                  @Param("offset") Integer offset,
                                  @Param("pageSize") Integer pageSize);

    Long countByCondition(@Param("userId") Long userId,
                          @Param("categoryId") Long categoryId,
                          @Param("state") Integer state);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(Long id);

    // ---- 互动冗余计数 ----
    // 都带 deleted = 0：已软删的文章不该再被点赞/收藏改计数（顺带补上写入路径缺 deleted 校验的欠账）
    // 减计数用 IF(...) 兜底：列是 int unsigned，直接 -1 到负数会报错/回绕

    @Update("UPDATE article SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementLikeCount(Long id);

    @Update("UPDATE article SET like_count = IF(like_count > 0, like_count - 1, 0) WHERE id = #{id} AND deleted = 0")
    int decrementLikeCount(Long id);

    @Update("UPDATE article SET collect_count = collect_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementCollectCount(Long id);

    @Update("UPDATE article SET collect_count = IF(collect_count > 0, collect_count - 1, 0) WHERE id = #{id} AND deleted = 0")
    int decrementCollectCount(Long id);
}
