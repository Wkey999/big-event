package com.itheima.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeaderboardSnapshotMapper {
    @Insert("INSERT IGNORE INTO leaderboard_sync_state(dimension) VALUES(#{dimension})")
    int ensureState(String dimension);

    @Select("SELECT * FROM leaderboard_sync_state WHERE dimension=#{dimension}")
    StateRow state(String dimension);

    @Select("SELECT * FROM leaderboard_sync_state WHERE dimension=#{dimension} FOR UPDATE")
    StateRow lockState(String dimension);

    @Select("SELECT * FROM leaderboard_snapshot WHERE id=#{id}")
    SnapshotRow snapshot(Long id);

    @Select("SELECT * FROM leaderboard_snapshot_item WHERE snapshot_id=#{snapshotId} ORDER BY rank_number, model_name")
    List<ItemRow> items(Long snapshotId);

    @Insert("INSERT INTO leaderboard_snapshot(dimension,metric,source_date,synced_at,sync_day,content_hash,item_count) " +
            "VALUES(#{dimension},#{metric},#{sourceDate},#{syncedAt},#{syncDay},#{contentHash},#{itemCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSnapshot(SnapshotRow snapshot);

    @Insert("<script>INSERT INTO leaderboard_snapshot_item" +
            "(snapshot_id,model_name,organization,`license`,rank_number,score,published_date," +
            "newly_listed,previous_rank,rank_change,rank_trend) VALUES " +
            "<foreach collection='items' item='item' separator=','>" +
            "(#{snapshotId},#{item.modelName},#{item.organization},#{item.license},#{item.rankNumber}," +
            "#{item.score},#{item.publishedDate},#{item.newlyListed},#{item.previousRank}," +
            "#{item.rankChange},#{item.rankTrend})</foreach></script>")
    int insertItems(@Param("snapshotId") Long snapshotId, @Param("items") List<ItemRow> items);

    @Update("UPDATE leaderboard_sync_state SET " +
            "attempt_count=CASE WHEN last_attempt_day=#{day} THEN attempt_count+1 ELSE 1 END," +
            "last_attempt_day=#{day},last_attempt_at=#{now},last_status=1,last_summary=#{summary}," +
            "last_success_snapshot_id=#{snapshotId},last_success_at=#{now} WHERE dimension=#{dimension}")
    int markSuccess(@Param("dimension") String dimension, @Param("day") LocalDate day,
                    @Param("now") LocalDateTime now, @Param("summary") String summary,
                    @Param("snapshotId") Long snapshotId);

    @Update("UPDATE leaderboard_sync_state SET " +
            "attempt_count=CASE WHEN last_attempt_day=#{day} THEN attempt_count+1 ELSE 1 END," +
            "last_attempt_day=#{day},last_attempt_at=#{now},last_status=2,last_summary=#{summary} " +
            "WHERE dimension=#{dimension}")
    int markFailure(@Param("dimension") String dimension, @Param("day") LocalDate day,
                    @Param("now") LocalDateTime now, @Param("summary") String summary);

    @Data
    class StateRow {
        private String dimension;
        private LocalDateTime lastAttemptAt;
        private LocalDate lastAttemptDay;
        private Integer attemptCount;
        private Integer lastStatus;
        private String lastSummary;
        private Long lastSuccessSnapshotId;
        private LocalDateTime lastSuccessAt;
    }

    @Data
    class SnapshotRow {
        private Long id;
        private String dimension;
        private String metric;
        private LocalDate sourceDate;
        private LocalDateTime syncedAt;
        private LocalDate syncDay;
        private String contentHash;
        private Integer itemCount;
    }

    @Data
    class ItemRow {
        private Long snapshotId;
        private String modelName;
        private String organization;
        private String license;
        private Integer rankNumber;
        private Double score;
        private LocalDate publishedDate;
        private Boolean newlyListed;
        private Integer previousRank;
        private Integer rankChange;
        private String rankTrend;
    }
}
