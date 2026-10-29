package com.itheima.service.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.mapper.LeaderboardSnapshotMapper.StateRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ModelLeaderboardServiceTest {
    private static final String TEXT_URL = "https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config=text_style_control&split=latest&offset=0&length=100";
    private static final String WEBDEV_URL = "https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config=webdev&split=latest&offset=0&length=100";
    private static final String RESPONSE = """
            {"rows":[
              {"row":{"model_name":"Model B","organization":"Org B","license":"MIT","rating":1400.5,"rank":2,"category":"overall","leaderboard_publish_date":"2026-10-23"}},
              {"row":{"model_name":"Model A","organization":"Org A","license":"Proprietary","rating":1442.0,"rank":1,"category":"overall","leaderboard_publish_date":"2026-10-23"}},
              {"row":{"model_name":"Different category","rating":1500,"rank":1,"category":"coding","leaderboard_publish_date":"2026-10-23"}}
            ]}
            """;
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    @Test
    void keepsOnlyPublishedOverallRowsAndSourceRank() throws Exception {
        var result = ModelLeaderboardService.parse(ModelLeaderboardService.Dimension.TEXT,
                new ObjectMapper().readTree(RESPONSE), NOW);
        assertEquals(2, result.items().size());
        assertEquals("Model A", result.items().getFirst().name());
        assertEquals(1, result.items().getFirst().rank());
        assertEquals("2026-10-23", result.sourceDate());
        assertEquals("available", result.status());
    }

    @Test
    void tiesHaveStableOrderAndSourceDateUsesLatestIncludedPublication() throws Exception {
        String response = """
                {"rows":[
                {"row":{"model_name":"B","rating":1400,"rank":1,"category":"overall","leaderboard_publish_date":"2026-10-22"}},
                {"row":{"model_name":"A","rating":1400,"rank":1,"category":"overall","leaderboard_publish_date":"2026-10-23"}}
                ]}
                """;
        var result = ModelLeaderboardService.parse(ModelLeaderboardService.Dimension.TEXT,
                new ObjectMapper().readTree(response), NOW);
        assertEquals("A", result.items().get(0).name());
        assertEquals("2026-10-23", result.sourceDate());
    }

    @Test
    void syncPersistsRealRowsAndVisitorReadsOnlyFromStore() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        LeaderboardSnapshotStore store = mock(LeaderboardSnapshotStore.class);
        var service = service(builder, store);

        service.sync(ModelLeaderboardService.Dimension.TEXT);
        verify(store).saveSuccess(eq(ModelLeaderboardService.Dimension.TEXT),
                argThat(board -> board.items().size() == 2 && "Arena Score".equals(board.metric())),
                matches("[0-9a-f]{64}"), eq(NOW), eq(LocalDate.of(2026, 10, 25)));
        service.get("text");
        service.get("text");
        verify(store, times(2)).current(ModelLeaderboardService.Dimension.TEXT);
        server.verify();
    }

    @Test
    void oneDimensionFailureDoesNotPreventAnotherFromSaving() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TEXT_URL)).andRespond(withServerError());
        server.expect(requestTo(WEBDEV_URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        LeaderboardSnapshotStore store = mock(LeaderboardSnapshotStore.class);
        var service = service(builder, store);

        service.sync(ModelLeaderboardService.Dimension.TEXT);
        service.sync(ModelLeaderboardService.Dimension.WEBDEV);

        verify(store).saveFailure(eq(ModelLeaderboardService.Dimension.TEXT), eq(NOW), any(), eq("上游 HTTP 500"));
        verify(store).saveSuccess(eq(ModelLeaderboardService.Dimension.WEBDEV), any(), anyString(), eq(NOW), any());
        verify(store, never()).saveSuccess(eq(ModelLeaderboardService.Dimension.TEXT), any(), anyString(), any(), any());
        server.verify();
    }

    @Test
    void failedSnapshotWriteIsRecordedAsDatabaseFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        LeaderboardSnapshotStore store = mock(LeaderboardSnapshotStore.class);
        doThrow(new IllegalStateException("db details must not leak")).when(store)
                .saveSuccess(eq(ModelLeaderboardService.Dimension.TEXT), any(), anyString(), any(), any());

        service(builder, store).sync(ModelLeaderboardService.Dimension.TEXT);

        verify(store).saveFailure(eq(ModelLeaderboardService.Dimension.TEXT), eq(NOW), any(),
                eq("快照写入失败：IllegalStateException"));
        server.verify();
    }

    @Test
    void agentUsesIpsScoreRatherThanArenaRating() throws Exception {
        String response = """
                {"rows":[{"row":{"model_name":"Agent Model","score":0.207665,"rating":1400,
                "rank":1,"category":"overall","leaderboard_publish_date":"2026-10-23"}}]}
                """;
        var result = ModelLeaderboardService.parse(ModelLeaderboardService.Dimension.AGENT,
                new ObjectMapper().readTree(response), NOW);
        assertEquals("IPS 分数", result.metric());
        assertEquals(0.207665, result.items().getFirst().score());
    }

    @Test
    void onlyFailedDimensionsRetryAfterFiveMinutesAndAtMostThreeAttempts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TEXT_URL)).andRespond(withServerError());
        LeaderboardSnapshotStore store = mock(LeaderboardSnapshotStore.class);
        StateRow failed = new StateRow();
        failed.setLastAttemptDay(LocalDate.of(2026, 10, 25));
        failed.setLastAttemptAt(LocalDateTime.ofInstant(NOW.minusSeconds(301), ZoneOffset.UTC));
        failed.setLastStatus(2);
        failed.setAttemptCount(2);
        when(store.state(ModelLeaderboardService.Dimension.TEXT)).thenReturn(failed);
        for (var dimension : ModelLeaderboardService.Dimension.values()) {
            if (dimension != ModelLeaderboardService.Dimension.TEXT) {
                StateRow successful = new StateRow();
                successful.setLastAttemptDay(LocalDate.of(2026, 10, 25));
                successful.setLastStatus(1);
                when(store.state(dimension)).thenReturn(successful);
            }
        }
        service(builder, store).retryFailedToday();
        verify(store).saveFailure(eq(ModelLeaderboardService.Dimension.TEXT), eq(NOW), any(), anyString());
        server.verify();
    }

    private static ModelLeaderboardService service(RestClient.Builder builder, LeaderboardSnapshotStore store) {
        return new ModelLeaderboardService(new ObjectMapper(), builder.build(),
                Clock.fixed(NOW, ZoneOffset.UTC), store, ZoneId.of("Asia/Shanghai"));
    }
}
