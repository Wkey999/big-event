package com.itheima.mapper;

import com.itheima.pojo.Article;
import com.itheima.pojo.PostMedia;
import com.itheima.pojo.PostMediaDTO;
import com.itheima.pojo.TopicSummary;
import com.itheima.pojo.TopicPost;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommunityMapper {

    @Select("SELECT * FROM article WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Article findArticleForUpdate(Long id);

    @Select("SELECT * FROM article WHERE id = #{id} AND deleted = 0")
    Article findArticle(Long id);

    @Select("SELECT * FROM post_media WHERE article_id = #{articleId} ORDER BY sort_order, id")
    List<PostMedia> listMedia(Long articleId);

    @Delete("DELETE FROM post_media WHERE article_id = #{articleId}")
    int deleteMedia(Long articleId);

    @Insert("INSERT INTO post_media(article_id, url, media_type, sort_order, width, height) " +
            "VALUES(#{articleId}, #{media.url}, #{media.mediaType}, #{sortOrder}, #{media.width}, #{media.height})")
    int insertMedia(@Param("articleId") Long articleId,
                    @Param("media") PostMediaDTO media,
                    @Param("sortOrder") int sortOrder);

    @Update("UPDATE post_media SET url = #{url} WHERE article_id = #{articleId} ORDER BY sort_order, id LIMIT 1")
    int updateFirstMediaUrl(@Param("articleId") Long articleId, @Param("url") String url);

    @Insert("INSERT IGNORE INTO topic(name) VALUES(#{name})")
    int insertTopicIfAbsent(String name);

    @Select("SELECT id FROM topic WHERE name = #{name}")
    Long findTopicIdByName(String name);

    @Select("SELECT topic_id FROM post_topic WHERE article_id = #{articleId} ORDER BY topic_id")
    List<Long> listTopicIdsForArticle(Long articleId);

    @Insert("INSERT INTO post_topic(article_id, topic_id) VALUES(#{articleId}, #{topicId})")
    int insertPostTopic(@Param("articleId") Long articleId, @Param("topicId") Long topicId);

    @Delete("DELETE FROM post_topic WHERE article_id = #{articleId} AND topic_id = #{topicId}")
    int deletePostTopic(@Param("articleId") Long articleId, @Param("topicId") Long topicId);

    @Update("UPDATE topic SET post_count = post_count + 1 WHERE id = #{topicId}")
    int incrementTopicPostCount(Long topicId);

    @Update("UPDATE topic SET post_count = post_count - 1 WHERE id = #{topicId} AND post_count > 0")
    int decrementTopicPostCount(Long topicId);

    @Delete("DELETE FROM post_topic WHERE article_id = #{articleId}")
    int deleteAllPostTopics(Long articleId);

    @Select({
            "<script>",
            "SELECT id, name, post_count AS postCount FROM topic WHERE status = 1",
            "<choose>",
            "<when test='sort == \"new\"'>ORDER BY create_time DESC, id DESC</when>",
            "<otherwise>ORDER BY hot_score DESC, post_count DESC, id DESC</otherwise>",
            "</choose>",
            "LIMIT #{limit}",
            "</script>"
    })
    List<TopicSummary> listTopics(@Param("sort") String sort, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM post_topic pt JOIN article a ON a.id = pt.article_id " +
            "JOIN topic t ON t.id = pt.topic_id WHERE pt.topic_id = #{topicId} " +
            "AND a.deleted = 0 AND a.state = 1 AND t.status = 1")
    long countPublishedTopicPosts(Long topicId);

    @Select("SELECT a.id, a.title, a.cover_img, a.summary, a.user_id, a.category_id, " +
            "a.state, a.view_count, a.like_count, a.collect_count, a.comment_count, a.create_time, a.update_time, " +
            "u.nickname AS author_nickname, u.avatar AS author_avatar " +
            "FROM post_topic pt JOIN article a ON a.id = pt.article_id LEFT JOIN user u ON u.id = a.user_id " +
            "JOIN topic t ON t.id = pt.topic_id WHERE pt.topic_id = #{topicId} " +
            "AND a.deleted = 0 AND a.state = 1 AND t.status = 1 " +
            "ORDER BY a.create_time DESC, a.id DESC LIMIT #{offset}, #{pageSize}")
    List<TopicPost> listPublishedTopicPosts(@Param("topicId") Long topicId,
                                            @Param("offset") int offset,
                                            @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(*) FROM topic WHERE id = #{id} AND status = 1")
    int countVisibleTopic(Long id);
}
