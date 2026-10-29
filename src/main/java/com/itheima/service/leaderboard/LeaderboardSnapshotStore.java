package com.itheima.service.leaderboard;

import com.itheima.mapper.LeaderboardSnapshotMapper;
import com.itheima.mapper.LeaderboardSnapshotMapper.ItemRow;
import com.itheima.mapper.LeaderboardSnapshotMapper.SnapshotRow;
import com.itheima.mapper.LeaderboardSnapshotMapper.StateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Each successful snapshot and its items commit atomically; failures only change sync state. */
@Service
@RequiredArgsConstructor
public class LeaderboardSnapshotStore {
    private final LeaderboardSnapshotMapper mapper;

    @Transactional(readOnly = true)
    public ModelLeaderboardService.Board current(ModelLeaderboardService.Dimension dimension) {
        StateRow state = mapper.state(dimension.key);
        if (state == null || state.getLastSuccessSnapshotId() == null) {
            return new ModelLeaderboardService.Board(dimension.key, dimension.label, dimension.metric,
                    ModelLeaderboardService.DATASET_URL, null, null, "unavailable", List.of(),
                    null, null, null, utc(state == null ? null : state.getLastAttemptAt()),
                    state == null ? "never" : attemptStatus(state.getLastStatus()),
                    state == null ? "尚无同步记录" : state.getLastSummary());
        }
        SnapshotRow snapshot = mapper.snapshot(state.getLastSuccessSnapshotId());
        if (snapshot == null || !dimension.key.equals(snapshot.getDimension())) {
            throw new IllegalStateException("Leaderboard state points to an invalid snapshot");
        }
        List<ModelLeaderboardService.Model> models = mapper.items(snapshot.getId()).stream()
                .map(item -> new ModelLeaderboardService.Model(item.getRankNumber(), item.getModelName(),
                        item.getOrganization(), item.getLicense(), item.getScore(),
                        item.getPublishedDate() == null ? null : item.getPublishedDate().toString(),
                        Boolean.TRUE.equals(item.getNewlyListed()), item.getPreviousRank(),
                        item.getRankChange(), item.getRankTrend()))
                .toList();
        return new ModelLeaderboardService.Board(dimension.key, dimension.label, snapshot.getMetric(),
                ModelLeaderboardService.DATASET_URL,
                snapshot.getSourceDate() == null ? null : snapshot.getSourceDate().toString(),
                utc(state.getLastSuccessAt()),
                Integer.valueOf(2).equals(state.getLastStatus()) ? "stale" : "available",
                models, snapshot.getId(), utc(snapshot.getSyncedAt()), utc(state.getLastSuccessAt()),
                utc(state.getLastAttemptAt()), attemptStatus(state.getLastStatus()), state.getLastSummary());
    }

    @Transactional(readOnly = true)
    public StateRow state(ModelLeaderboardService.Dimension dimension) {
        return mapper.state(dimension.key);
    }

    @Transactional
    public void saveSuccess(ModelLeaderboardService.Dimension dimension,
                            ModelLeaderboardService.Board fetched, String hash,
                            Instant now, LocalDate day) {
        if (fetched.items().isEmpty()) throw new IllegalArgumentException("Empty leaderboard is not a valid snapshot");
        mapper.ensureState(dimension.key);
        StateRow state = mapper.lockState(dimension.key);
        SnapshotRow previous = state.getLastSuccessSnapshotId() == null ? null
                : mapper.snapshot(state.getLastSuccessSnapshotId());
        if (state.getLastSuccessSnapshotId() != null
                && (previous == null || !dimension.key.equals(previous.getDimension()))) {
            throw new IllegalStateException("Leaderboard state points to an invalid snapshot");
        }
        if (previous != null && day.equals(previous.getSyncDay()) && hash.equals(previous.getContentHash())) {
            mapper.markSuccess(dimension.key, day, LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                    "同步成功，今日快照内容未变化", previous.getId());
            return;
        }

        List<ItemRow> oldItems = previous == null ? List.of() : mapper.items(previous.getId());
        SnapshotRow snapshot = new SnapshotRow();
        snapshot.setDimension(dimension.key);
        snapshot.setMetric(dimension.metric);
        snapshot.setSourceDate(date(fetched.sourceDate()));
        snapshot.setSyncedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        snapshot.setSyncDay(day);
        snapshot.setContentHash(hash);
        snapshot.setItemCount(fetched.items().size());
        mapper.insertSnapshot(snapshot);
        List<ItemRow> items = compare(snapshot.getId(), fetched.items(), oldItems, previous != null);
        mapper.insertItems(snapshot.getId(), items);
        mapper.markSuccess(dimension.key, day, snapshot.getSyncedAt(), "同步成功，已保存快照", snapshot.getId());
    }

    @Transactional
    public void saveFailure(ModelLeaderboardService.Dimension dimension, Instant now,
                            LocalDate day, String summary) {
        mapper.ensureState(dimension.key);
        mapper.lockState(dimension.key);
        mapper.markFailure(dimension.key, day, LocalDateTime.ofInstant(now, ZoneOffset.UTC), summary);
    }

    static List<ItemRow> compare(Long snapshotId, List<ModelLeaderboardService.Model> current,
                                 List<ItemRow> previous, boolean hasBaseline) {
        Map<String, Integer> oldRanks = new HashMap<>();
        for (ItemRow item : previous) oldRanks.put(item.getModelName(), item.getRankNumber());
        return current.stream().map(model -> {
            ItemRow item = new ItemRow();
            item.setSnapshotId(snapshotId);
            item.setModelName(model.name());
            item.setOrganization(model.organization());
            item.setLicense(model.license());
            item.setRankNumber(model.rank());
            item.setScore(model.score());
            item.setPublishedDate(date(model.publishedDate()));
            Integer oldRank = oldRanks.get(model.name());
            item.setPreviousRank(oldRank);
            item.setNewlyListed(hasBaseline && oldRank == null);
            if (oldRank != null) {
                int change = oldRank - model.rank();
                item.setRankChange(change);
                item.setRankTrend(change > 0 ? "up" : change < 0 ? "down" : "same");
            } else if (hasBaseline) {
                item.setRankTrend("new");
            }
            return item;
        }).toList();
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static String utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static String attemptStatus(Integer code) {
        return Integer.valueOf(1).equals(code) ? "success" : Integer.valueOf(2).equals(code) ? "failed" : "never";
    }
}
