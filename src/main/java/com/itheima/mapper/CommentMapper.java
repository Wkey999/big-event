package com.itheima.mapper;

import com.itheima.pojo.Comment;
import com.itheima.pojo.CommentReplyView;
import com.itheima.pojo.CommentView;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Select("SELECT * FROM comment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Comment findByIdForUpdate(Long id);

    @Select("SELECT * FROM comment WHERE id = #{id} AND deleted = 0 AND state = 1")
    Comment findVisibleById(Long id);

    @Select("SELECT COUNT(*) FROM comment WHERE article_id = #{articleId} AND root_id IS NULL " +
            "AND deleted = 0 AND state = 1")
    long countRoots(Long articleId);

    @Select("SELECT c.id, c.user_id AS userId, u.nickname, u.avatar, c.content, " +
            "c.like_count AS likeCount, c.reply_count AS replyCount, c.create_time AS createTime " +
            "FROM comment c JOIN user u ON u.id = c.user_id WHERE c.article_id = #{articleId} " +
            "AND c.root_id IS NULL AND c.deleted = 0 AND c.state = 1 " +
            "ORDER BY c.create_time DESC, c.id DESC LIMIT #{offset}, #{pageSize}")
    List<CommentView> listRoots(@Param("articleId") Long articleId,
                                @Param("offset") int offset,
                                @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM comment WHERE root_id = #{rootId} AND deleted = 0 AND state = 1")
    long countReplies(Long rootId);

    @Select("SELECT c.id, c.parent_id AS parentId, u.nickname, c.content, " +
            "c.like_count AS likeCount, c.create_time AS createTime " +
            "FROM comment c JOIN user u ON u.id = c.user_id WHERE c.root_id = #{rootId} " +
            "AND c.deleted = 0 AND c.state = 1 ORDER BY c.create_time ASC, c.id ASC " +
            "LIMIT #{offset}, #{pageSize}")
    List<CommentReplyView> listReplies(@Param("rootId") Long rootId,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    @Select("SELECT c.id, c.parent_id AS parentId, u.nickname, c.content, " +
            "c.like_count AS likeCount, c.create_time AS createTime " +
            "FROM comment c JOIN user u ON u.id = c.user_id WHERE c.root_id = #{rootId} " +
            "AND c.deleted = 0 AND c.state = 1 ORDER BY c.create_time ASC, c.id ASC LIMIT #{limit}")
    List<CommentReplyView> firstReplies(@Param("rootId") Long rootId, @Param("limit") int limit);

    @Insert("INSERT INTO comment(article_id, user_id, root_id, parent_id, content) " +
            "VALUES(#{articleId}, #{userId}, #{rootId}, #{parentId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT COUNT(*) FROM comment WHERE id = #{rootId} AND article_id = #{articleId} " +
            "AND root_id IS NULL AND deleted = 0 AND state = 1")
    int countValidRoot(@Param("rootId") Long rootId, @Param("articleId") Long articleId);

    @Select("SELECT COUNT(*) FROM comment WHERE id = #{parentId} AND article_id = #{articleId} " +
            "AND (id = #{rootId} OR root_id = #{rootId}) AND deleted = 0 AND state = 1")
    int countValidParent(@Param("parentId") Long parentId,
                         @Param("articleId") Long articleId,
                         @Param("rootId") Long rootId);

    @Update("UPDATE comment SET reply_count = reply_count + 1 WHERE id = #{rootId} " +
            "AND root_id IS NULL AND deleted = 0 AND state = 1")
    int incrementReplyCount(Long rootId);

    @Update("UPDATE comment SET reply_count = IF(reply_count > 0, reply_count - 1, 0) " +
            "WHERE id = #{rootId} AND root_id IS NULL")
    int decrementReplyCount(Long rootId);

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{id} AND deleted = 0 AND state = 1")
    int incrementLikeCount(Long id);

    @Update("UPDATE comment SET like_count = IF(like_count > 0, like_count - 1, 0) " +
            "WHERE id = #{id} AND deleted = 0 AND state = 1")
    int decrementLikeCount(Long id);

    @Select("SELECT COUNT(*) FROM comment WHERE root_id = #{rootId} AND deleted = 0 AND state = 1")
    int countUndeletedReplies(Long rootId);

    @Update("UPDATE comment SET deleted = 1 WHERE root_id = #{rootId} AND deleted = 0")
    int softDeleteReplies(Long rootId);

    @Update("UPDATE comment SET deleted = 1, reply_count = 0 WHERE id = #{id} AND deleted = 0")
    int softDeleteRoot(Long id);

    @Update("UPDATE comment SET deleted = 1 WHERE id = #{id} AND deleted = 0 AND root_id IS NOT NULL")
    int softDeleteReply(Long id);
}
