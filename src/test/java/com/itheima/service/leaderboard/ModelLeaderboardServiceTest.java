package com.itheima.service.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class ModelLeaderboardServiceTest {
    private static final String RESPONSE = """
            {"rows":[
              {"row":{"model_name":"Model B","organization":"Org B","license":"MIT","rating":1400.5,"rank":2,"category":"overall","leaderboard_publish_date":"2026-10-23"}},
              {"row":{"model_name":"Model A","organization":"Org A","license":"Proprietary","rating":1442.0,"rank":1,"category":"overall","leaderboard_publish_date":"2026-10-23"}},
              {"row":{"model_name":"Different category","rating":1500,"rank":1,"category":"coding","leaderboard_publish_date":"2026-10-23"}}
            ]}
            """;

    @Test
    void keepsOnlyPublishedOverallRowsAndSourceRank() throws Exception {
        var result = ModelLeaderboardService.parse(ModelLeaderboardService.Dimension.TEXT,
                new ObjectMapper().readTree(RESPONSE), Instant.parse("2026-10-25T00:00:00Z"));
        assertEquals(2, result.items().size());
        assertEquals("Model A", result.items().getFirst().name());
        assertEquals(1, result.items().getFirst().rank());
        assertEquals("2026-10-23", result.sourceDate());
        assertEquals("available", result.status());
    }

    @Test
    void cachesSuccessfulResponseInsteadOfCallingUpstreamPerVisitor() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config=text_style_control&split=latest&offset=0&length=100"))
                .andRespond(withSuccess(RESPONSE, org.springframework.http.MediaType.APPLICATION_JSON));
        var service = new ModelLeaderboardService(new ObjectMapper(), builder.build(),
                Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC));
        assertEquals(2, service.get("text").items().size());
        assertEquals(2, service.get("text").items().size());
        server.verify();
    }

    @Test
    void agentUsesItsOwnMetricAndNeverSubstitutesArenaRating() throws Exception {
        String response = """
                {"rows":[{"row":{"model_name":"Agent Model","score":0.207665,"rating":1400,
                "rank":1,"category":"overall","leaderboard_publish_date":"2026-10-23"}}]}
                """;
        var result = ModelLeaderboardService.parse(ModelLeaderboardService.Dimension.AGENT,
                new ObjectMapper().readTree(response), Instant.parse("2026-10-25T00:00:00Z"));
        assertEquals("IPS 分数", result.metric());
        assertEquals(0.207665, result.items().getFirst().score());
    }

    @Test
    void upstreamFailureReturnsAnEmptyUnavailableBoardRatherThanMadeUpResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config=text_style_control&split=latest&offset=0&length=100"))
                .andRespond(withServerError());
        var service = new ModelLeaderboardService(new ObjectMapper(), builder.build(),
                Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC));
        var board = service.get("text");
        assertEquals("unavailable", board.status());
        assertTrue(board.items().isEmpty());
        server.verify();
    }

}
