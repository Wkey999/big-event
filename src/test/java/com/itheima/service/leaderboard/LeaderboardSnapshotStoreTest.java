package com.itheima.service.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.mapper.LeaderboardSnapshotMapper;
import com.itheima.mapper.LeaderboardSnapshotMapper.ItemRow;
import com.itheima.mapper.LeaderboardSnapshotMapper.SnapshotRow;
import com.itheima.mapper.LeaderboardSnapshotMapper.StateRow;
import com.itheima.pojo.Result;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaderboardSnapshotStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);
    private static final ModelLeaderboardService.Dimension TEXT = ModelLeaderboardService.Dimension.TEXT;

    @Test
    void firstSnapshotEstablishesBaselineWithoutMarkingEveryModelNew() {
        List<ItemRow> items = LeaderboardSnapshotStore.compare(8L, List.of(model("A", 1)), List.of(), false);
        assertFalse(items.getFirst().getNewlyListed());
        assertNull(items.getFirst().getPreviousRank());
        assertNull(items.getFirst().getRankChange());
        assertNull(items.getFirst().getRankTrend());
    }

    @Test
    void comparesNewListingAndBothDirectionsWithinOneDimension() {
        List<ItemRow> items = LeaderboardSnapshotStore.compare(9L,
                List.of(model("B", 1), model("A", 2), model("C", 3), model("D", 4)),
                List.of(old("A", 1), old("B", 3), old("D", 4)), true);
        assertEquals(2, items.get(0).getRankChange());
        assertEquals("up", items.get(0).getRankTrend());
        assertEquals(-1, items.get(1).getRankChange());
        assertEquals("down", items.get(1).getRankTrend());
        assertTrue(items.get(2).getNewlyListed());
        assertNull(items.get(2).getPreviousRank());
        assertEquals("new", items.get(2).getRankTrend());
        assertEquals(0, items.get(3).getRankChange());
        assertEquals("same", items.get(3).getRankTrend());
    }

    @Test
    void sameDaySameContentReusesSnapshotAndOnlyUpdatesSuccessfulCheckTime() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        StateRow state = state(1, 10L);
        SnapshotRow previous = snapshot(10L, DAY, "abc");
        when(mapper.lockState("text")).thenReturn(state);
        when(mapper.snapshot(10L)).thenReturn(previous);

        new LeaderboardSnapshotStore(mapper).saveSuccess(TEXT, board(model("A", 1)), "abc", NOW, DAY);

        verify(mapper, never()).insertSnapshot(any());
        verify(mapper, never()).insertItems(anyLong(), anyList());
        verify(mapper).markSuccess(eq("text"), eq(DAY), any(), contains("未变化"), eq(10L));
    }

    @Test
    void nextDayRetainsAnArchiveEvenIfUpstreamContentIsUnchanged() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        when(mapper.lockState("text")).thenReturn(state(1, 10L));
        when(mapper.snapshot(10L)).thenReturn(snapshot(10L, DAY.minusDays(1), "abc"));
        when(mapper.items(10L)).thenReturn(List.of(old("A", 1)));
        doAnswer(invocation -> {
            SnapshotRow inserted = invocation.getArgument(0);
            inserted.setId(11L);
            return 1;
        }).when(mapper).insertSnapshot(any());

        new LeaderboardSnapshotStore(mapper).saveSuccess(TEXT, board(model("A", 1)), "abc", NOW, DAY);

        @SuppressWarnings("unchecked")
        var captured = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(mapper).insertItems(eq(11L), captured.capture());
        ItemRow archived = (ItemRow) captured.getValue().getFirst();
        assertFalse(archived.getNewlyListed());
        assertEquals(0, archived.getRankChange());
        verify(mapper).markSuccess(eq("text"), eq(DAY), any(), anyString(), eq(11L));
    }

    @Test
    void failedSyncNeverCreatesAFalseSnapshotOrReplacesSuccessPointer() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        new LeaderboardSnapshotStore(mapper).saveFailure(TEXT, NOW, DAY, "上游 HTTP 503");
        verify(mapper).markFailure(eq("text"), eq(DAY), any(), eq("上游 HTTP 503"));
        verify(mapper, never()).insertSnapshot(any());
        verify(mapper, never()).markSuccess(anyString(), any(), any(), anyString(), anyLong());
    }

    @Test
    void newServiceInstanceReadsLastCommittedSnapshotAfterRestartAndShowsFailure() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        StateRow state = state(2, 10L);
        state.setLastAttemptAt(LocalDateTime.parse("2026-10-25T00:00:00"));
        state.setLastSuccessAt(LocalDateTime.parse("2026-10-24T00:00:00"));
        state.setLastSummary("上游 HTTP 503");
        when(mapper.state("text")).thenReturn(state);
        when(mapper.snapshot(10L)).thenReturn(snapshot(10L, DAY.minusDays(1), "abc"));
        ItemRow persisted = old("A", 1);
        persisted.setScore(1442.0);
        persisted.setNewlyListed(true);
        persisted.setRankTrend("new");
        when(mapper.items(10L)).thenReturn(List.of(persisted));

        ModelLeaderboardService.Board restored = new LeaderboardSnapshotStore(mapper).current(TEXT);

        assertEquals("stale", restored.status());
        assertEquals("failed", restored.lastAttemptStatus());
        assertEquals("2026-10-24T00:00:00Z", restored.lastSuccessfulSyncAt());
        assertEquals("2026-10-23", restored.sourceDate());
        assertEquals(1442.0, restored.items().getFirst().score());
        assertTrue(restored.items().getFirst().newlyListed());
    }

    @Test
    void missingSuccessfulSnapshotStaysUnavailableEvenAfterFailure() {
        LeaderboardSnapshotMapper mapper = mock(LeaderboardSnapshotMapper.class);
        StateRow failed = state(2, null);
        failed.setLastSummary("上游 HTTP 503");
        when(mapper.state("text")).thenReturn(failed);
        var board = new LeaderboardSnapshotStore(mapper).current(TEXT);
        assertEquals("unavailable", board.status());
        assertEquals("failed", board.lastAttemptStatus());
        assertTrue(board.items().isEmpty());
        verify(mapper, never()).items(anyLong());
    }

    @Test
    void expandedResponseKeepsFieldsUsedByCurrentFrontend() {
        var payload = new ObjectMapper().valueToTree(Result.success(board(model("A", 1))));
        assertEquals(0, payload.path("code").asInt());
        assertEquals("text", payload.path("data").path("dimension").asText());
        assertEquals("2026-10-23", payload.path("data").path("sourceDate").asText());
        assertEquals(1, payload.path("data").path("items").get(0).path("rank").asInt());
        assertTrue(payload.path("data").has("lastSuccessfulSyncAt"));
        assertTrue(payload.path("data").path("items").get(0).has("newlyListed"));
    }

    private static ModelLeaderboardService.Model model(String name, int rank) {
        return new ModelLeaderboardService.Model(rank, name, "Org", "MIT", 1442.0,
                "2026-10-23", false, null, null, null);
    }

    private static ModelLeaderboardService.Board board(ModelLeaderboardService.Model... models) {
        return new ModelLeaderboardService.Board("text", "对话", "Arena Score",
                ModelLeaderboardService.DATASET_URL, "2026-10-23", NOW.toString(), "available",
                List.of(models), null, null, null, null, "never", "");
    }

    private static ItemRow old(String name, int rank) {
        ItemRow item = new ItemRow();
        item.setModelName(name);
        item.setRankNumber(rank);
        item.setOrganization("Org");
        item.setLicense("MIT");
        item.setScore(1442.0);
        return item;
    }

    private static SnapshotRow snapshot(long id, LocalDate day, String hash) {
        SnapshotRow row = new SnapshotRow();
        row.setId(id);
        row.setDimension("text");
        row.setMetric("Arena Score");
        row.setSourceDate(LocalDate.of(2026, 10, 23));
        row.setSyncDay(day);
        row.setSyncedAt(LocalDateTime.of(2026, 10, 24, 0, 0));
        row.setContentHash(hash);
        return row;
    }

    private static StateRow state(int status, Long snapshotId) {
        StateRow row = new StateRow();
        row.setDimension("text");
        row.setLastStatus(status);
        row.setLastSuccessSnapshotId(snapshotId);
        return row;
    }
}
