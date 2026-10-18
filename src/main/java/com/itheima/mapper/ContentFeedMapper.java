package com.itheima.mapper;
import com.itheima.pojo.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ContentFeedMapper {
    @Select("SELECT COUNT(*) FROM feed_entry WHERE source_key=#{source} AND status=0 AND translated_title IS NULL")
    int countUntranslated(String source);
    @Select("SELECT * FROM feed_entry WHERE source_key=#{source} AND status=0 AND translated_title IS NULL ORDER BY id DESC LIMIT #{limit}")
    List<FeedEntry> findUntranslated(@Param("source") String source,@Param("limit") int limit);
    @Update("UPDATE feed_entry SET translated_title=#{title} WHERE id=#{id} AND status=0 AND translated_title IS NULL")
    int saveTranslation(@Param("id") Long id,@Param("title") String title);
    @Update("UPDATE feed_entry SET translated_title=#{translatedTitle} WHERE id=#{id} AND status=0")
    int updateReviewedTitle(FeedEntry entry);
    @Select("SELECT COUNT(*) FROM feed_entry WHERE fetched_at >= #{dayStart}")
    long countSince(java.time.LocalDateTime dayStart);
    @Select("SELECT COUNT(*) FROM feed_entry WHERE source_key=#{source} AND fetched_at >= #{dayStart}")
    long countSourceSince(@Param("source") String source,@Param("dayStart") java.time.LocalDateTime dayStart);
    @Insert("INSERT INTO feed_entry(source_key,source_name,source_url,url_hash,title,source_author,source_published_at,fetched_at) VALUES(#{sourceKey},#{sourceName},#{sourceUrl},#{urlHash},#{title},#{sourceAuthor},#{sourcePublishedAt},#{fetchedAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertEntry(FeedEntry entry);
    @Select("<script>SELECT * FROM feed_entry WHERE 1=1 <if test='status != null'>AND status=#{status}</if><if test='source != null'>AND source_key=#{source}</if> ORDER BY id DESC LIMIT #{offset},#{size}</script>")
    List<FeedEntry> listEntries(@Param("status") Integer status,@Param("source") String source,@Param("offset") int offset,@Param("size") int size);
    @Select("<script>SELECT COUNT(*) FROM feed_entry WHERE 1=1 <if test='status != null'>AND status=#{status}</if><if test='source != null'>AND source_key=#{source}</if></script>")
    long countEntries(@Param("status") Integer status,@Param("source") String source);
    @Select("SELECT * FROM feed_entry WHERE id=#{id} FOR UPDATE")
    FeedEntry lockEntry(Long id);
    @Update("UPDATE feed_entry SET status=#{status},article_id=#{articleId},reviewed_by=#{userId},reviewed_at=NOW() WHERE id=#{id} AND status=0")
    int review(@Param("id") Long id,@Param("status") int status,@Param("articleId") Long articleId,@Param("userId") Long userId);
    @Insert("INSERT INTO feed_run(source_key,started_at,finished_at,scanned,inserted,duplicates,filtered,success,message) VALUES(#{sourceKey},#{startedAt},#{finishedAt},#{scanned},#{inserted},#{duplicates},#{filtered},#{success},#{message})")
    int insertRun(FeedRun run);
    @Select("SELECT * FROM feed_run WHERE source_key=#{source} ORDER BY id DESC LIMIT 1")
    FeedRun latestRun(String source);
    @Insert("INSERT INTO user(username,password,nickname,role,user_type,status,login_enabled) VALUES(#{username},#{password},#{nickname},0,1,1,0)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertBot(User user);
    @Insert("INSERT INTO article(title,content,summary,cover_img,user_id,category_id,state) VALUES(#{title},'','','',#{userId},#{categoryId},1)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertLinkArticle(Article article);
    @Insert("INSERT INTO article_source(article_id,source_name,source_url,source_title,source_translated_title,source_author,source_published_at,source_fetched_at) VALUES(#{articleId},#{sourceName},#{sourceUrl},#{title},#{translatedTitle},#{sourceAuthor},#{sourcePublishedAt},#{fetchedAt})")
    int insertArticleSource(FeedEntry entry);
}
