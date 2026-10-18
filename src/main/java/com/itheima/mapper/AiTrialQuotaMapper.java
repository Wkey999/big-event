package com.itheima.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;

@Mapper
public interface AiTrialQuotaMapper {
    // ON DUPLICATE KEY UPDATE acquires an exclusive lock, avoiding INSERT IGNORE's
    // shared-lock upgrade deadlock when many callers create/read the same daily row.
    @Insert("INSERT INTO ai_trial_daily_usage(usage_date, scope_id, used_count) VALUES(#{date}, #{scope}, 0) " +
            "ON DUPLICATE KEY UPDATE used_count=used_count")
    int ensureRow(@Param("date") LocalDate date, @Param("scope") String scope);

    @Select("SELECT used_count FROM ai_trial_daily_usage WHERE usage_date=#{date} AND scope_id=#{scope} FOR UPDATE")
    int lockCount(@Param("date") LocalDate date, @Param("scope") String scope);

    @Select("SELECT used_count FROM ai_trial_daily_usage WHERE usage_date=#{date} AND scope_id=#{scope}")
    Integer count(@Param("date") LocalDate date, @Param("scope") String scope);

    @Update("UPDATE ai_trial_daily_usage SET used_count=used_count+1 WHERE usage_date=#{date} AND scope_id=#{scope}")
    int increment(@Param("date") LocalDate date, @Param("scope") String scope);
}
