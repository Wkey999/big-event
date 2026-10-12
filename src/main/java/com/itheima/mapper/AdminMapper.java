package com.itheima.mapper;

import com.itheima.pojo.AdminUserRow;
import com.itheima.pojo.AdminArticleListItem;
import com.itheima.pojo.AdminDailyStat;
import com.itheima.pojo.AdminKeywordStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminMapper {

    @Select({
            "<script>",
            "SELECT a.id, a.title, a.cover_img, a.summary, a.user_id, a.category_id, a.state,",
            "a.view_count, a.like_count, a.collect_count, a.comment_count, a.create_time, a.update_time,",
            "u.nickname AS author_nickname, u.avatar AS author_avatar",
            "FROM article a LEFT JOIN user u ON u.id = a.user_id",
            "WHERE a.deleted = 0",
            "<if test='state != null'>AND a.state = #{state}</if>",
            "<if test='authorId != null'>AND a.user_id = #{authorId}</if>",
            "<if test='categoryId != null'>AND a.category_id = #{categoryId}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.summary LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY a.create_time DESC, a.id DESC LIMIT #{offset}, #{pageSize}",
            "</script>"
    })
    List<AdminArticleListItem> findAdminArticles(@Param("state") Integer state,
                                                 @Param("authorId") Long authorId,
                                                 @Param("categoryId") Long categoryId,
                                                 @Param("keyword") String keyword,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM article a WHERE a.deleted = 0",
            "<if test='state != null'>AND a.state = #{state}</if>",
            "<if test='authorId != null'>AND a.user_id = #{authorId}</if>",
            "<if test='categoryId != null'>AND a.category_id = #{categoryId}</if>",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (a.title LIKE CONCAT('%', #{keyword}, '%') OR a.summary LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</script>"
    })
    Long countAdminArticles(@Param("state") Integer state,
                            @Param("authorId") Long authorId,
                            @Param("categoryId") Long categoryId,
                            @Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT u.id, u.username, u.nickname, u.email, u.avatar, u.role, u.user_type AS userType,",
            "u.status, u.login_enabled AS loginEnabled, u.create_time AS createTime",
            "FROM user u WHERE u.deleted = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='role != null'>AND u.role = #{role}</if>",
            "<if test='userType != null'>AND u.user_type = #{userType}</if>",
            "ORDER BY u.create_time DESC, u.id DESC LIMIT #{offset}, #{pageSize}",
            "</script>"
    })
    List<AdminUserRow> findAdminUsers(@Param("keyword") String keyword,
                                      @Param("role") Integer role,
                                      @Param("userType") Integer userType,
                                      @Param("offset") int offset,
                                      @Param("pageSize") int pageSize);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM user u WHERE u.deleted = 0",
            "<if test='keyword != null and keyword != \"\"'>",
            "AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test='role != null'>AND u.role = #{role}</if>",
            "<if test='userType != null'>AND u.user_type = #{userType}</if>",
            "</script>"
    })
    Long countAdminUsers(@Param("keyword") String keyword,
                         @Param("role") Integer role,
                         @Param("userType") Integer userType);

    @Select("SELECT COUNT(*) FROM user WHERE deleted = 0")
    Long countUsers();

    @Select("SELECT COUNT(*) FROM article WHERE deleted = 0")
    Long countArticles();

    @Select("SELECT COUNT(*) FROM article WHERE deleted = 0 AND state = 0")
    Long countDraftArticles();

    @Select("SELECT COUNT(*) FROM article WHERE deleted = 0 AND state = 1")
    Long countPublishedArticles();

    @Select("SELECT COUNT(*) FROM article WHERE deleted = 0 AND create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY")
    Long countTodayArticles();

    @Select("SELECT COUNT(*) FROM search_log WHERE create_time >= #{since}")
    Long countSearchesSince(LocalDateTime since);

    @Select("SELECT COALESCE(AVG(CASE WHEN result_count = 0 THEN 1.0 ELSE 0.0 END), 0.0) FROM search_log WHERE create_time >= #{since}")
    Double searchZeroResultRateSince(LocalDateTime since);

    @Select("SELECT COALESCE(AVG(elapsed_ms), 0.0) FROM search_log WHERE create_time >= #{since}")
    Double averageSearchElapsedSince(LocalDateTime since);

    @Select("SELECT keyword, COUNT(*) AS count FROM search_log WHERE create_time >= #{since} AND keyword <> '' GROUP BY keyword ORDER BY count DESC, keyword ASC LIMIT #{limit}")
    List<AdminKeywordStat> topSearchKeywordsSince(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Select("SELECT DATE_FORMAT(MIN(create_time), '%Y-%m-%d') AS day, COUNT(*) AS count, 0.0 AS successRate FROM search_log WHERE create_time >= #{since} GROUP BY DATE(create_time) ORDER BY DATE(create_time)")
    List<AdminDailyStat> searchDailyTrendSince(LocalDateTime since);

    @Select("SELECT COUNT(*) FROM ask_log WHERE create_time >= #{since}")
    Long countAsksSince(LocalDateTime since);

    @Select("SELECT COALESCE(AVG(elapsed_ms), 0.0) FROM ask_log WHERE create_time >= #{since}")
    Double averageAskElapsedSince(LocalDateTime since);

    @Select("SELECT COALESCE(AVG(reference_count), 0.0) FROM ask_log WHERE create_time >= #{since}")
    Double averageAskReferencesSince(LocalDateTime since);

    @Select("SELECT COALESCE(AVG(success), 0.0) FROM ask_log WHERE create_time >= #{since}")
    Double askSuccessRateSince(LocalDateTime since);

    @Select("SELECT DATE_FORMAT(MIN(create_time), '%Y-%m-%d') AS day, COUNT(*) AS count, COALESCE(AVG(success), 0.0) AS successRate FROM ask_log WHERE create_time >= #{since} GROUP BY DATE(create_time) ORDER BY DATE(create_time)")
    List<AdminDailyStat> askDailyTrendSince(LocalDateTime since);
}
