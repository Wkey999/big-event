package com.itheima.controller;

import com.itheima.exception.BusinessException;
import com.itheima.interceptor.JwtInterceptor;
import com.itheima.service.agent.AiSearchClientRouter;
import com.itheima.service.agent.AiTrialQuotaService;
import com.itheima.service.security.AccountAccessCache;
import com.itheima.service.security.ClientAddressResolver;
import com.itheima.service.security.TokenRevocationService;
import com.itheima.utils.JwtUtils;
import com.itheima.utils.ThreadLocalUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentAssistantControllerTest {
    @AfterEach void clearContext() { ThreadLocalUtils.clear(); }

    @Test
    void rejectsNonLoopbackServiceAndDisabledMode() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentAssistantController(true, "http://example.org:8001"));
        var disabled = new DocumentAssistantController(false, "http://127.0.0.1:8001");
        assertThrows(BusinessException.class,
                () -> disabled.analyze(new DocumentAssistantController.AnalyzeRequest("资料", "问题")));
    }

    @Test
    void authenticatedGatewayForwardsToLocalPythonContract() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze", exchange -> {
            byte[] body = "{\"mode\":\"extractive\",\"summary\":\"原文摘要\",\"answer\":\"请核对片段\",\"citations\":[{\"chunk\":1,\"excerpt\":\"原文片段\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var jwt = mock(JwtUtils.class);
            var cache = mock(AccountAccessCache.class);
            var revocation = mock(TokenRevocationService.class);
            when(jwt.getUserId("ok")).thenReturn(7L);
            when(cache.get(7L)).thenReturn(new AccountAccessCache.Account(7L, true, false));
            var controller = new DocumentAssistantController(true,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .addInterceptors(new JwtInterceptor(jwt, cache, revocation)).build();
            var request = post("/document/analyze").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"测试资料\",\"question\":\"什么内容\"}");
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(post("/document/analyze").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer ok")
                            .content("{\"text\":\"测试资料\",\"question\":\"什么内容\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mode").value("extractive"))
                    .andExpect(jsonPath("$.data.citations[0].excerpt").value("原文片段"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void verifyAndSanitizeCitationsStripsHallucinatedChunkIdsAndOrdersCitedFirst() {
        var c1 = new DocumentAssistantController.Citation(1, "第一章总则", 1.2, 1);
        var c2 = new DocumentAssistantController.Citation(2, "报名截止日期为11月5日", 4.8, 2);
        var verified = DocumentAssistantController.verifyAndSanitizeCitations(
                "报名截止日期是 11 月 5 日 [#2] [#99]。", List.of(c1, c2));
        assertEquals("报名截止日期是 11 月 5 日 [#2]。", verified.sanitizedAnswer());
        assertEquals(List.of(2), verified.verifiedChunkIds());
        assertEquals(2, verified.orderedCitations().get(0).chunk());
        assertEquals(1, verified.orderedCitations().get(1).chunk());
    }

    @Test
    void ragModeRefusesWithoutCallingModelOrReservingQuotaWhenNoCitationsFound() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze", exchange -> {
            byte[] body = "{\"mode\":\"extractive\",\"strategy\":\"bm25\",\"chunk_count\":3,\"summary\":\"摘要\",\"answer\":\"未找到\",\"citations\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var router = mock(AiSearchClientRouter.class);
            var quota = mock(AiTrialQuotaService.class);
            var addr = mock(ClientAddressResolver.class);
            var controller = new DocumentAssistantController(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), router, quota, addr);
            var res = controller.analyze(new DocumentAssistantController.AnalyzeRequest(
                    "活动报名截止日期是11月5日。", "食堂饭卡去哪补办？", "platform", "bm25", null));
            assertEquals("rag-refused", res.getData().mode());
            assertTrue(res.getData().answer().contains("未消耗 AI 额度"));
            verifyNoInteractions(router, quota);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ragModeReservesQuotaAndVerifiesCitationsOnGroundedAnswer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/analyze", exchange -> {
            byte[] body = "{\"mode\":\"extractive\",\"strategy\":\"bm25\",\"chunk_count\":2,\"summary\":\"摘要\",\"answer\":\"请核对\",\"citations\":[{\"chunk\":2,\"excerpt\":\"截止时间为11月5日17:00\",\"score\":4.5,\"page\":1}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            ThreadLocalUtils.setUserId(9L);
            var router = mock(AiSearchClientRouter.class);
            var chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
            var quota = mock(AiTrialQuotaService.class);
            var addr = mock(ClientAddressResolver.class);
            when(addr.current()).thenReturn("127.0.0.1");
            when(router.create("platform", null)).thenReturn(chatClient);
            when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                    .thenReturn("报名将于 11 月 5 日 17:00 截止 [#2] [#88]。");

            var controller = new DocumentAssistantController(
                    true, "http://127.0.0.1:" + server.getAddress().getPort(), router, quota, addr);
            var res = controller.analyze(new DocumentAssistantController.AnalyzeRequest(
                    "报名通知正文", "什么时候截止？", "platform", "bm25", null));

            verify(quota).reserve(9L, "127.0.0.1");
            assertEquals("rag", res.getData().mode());
            assertEquals("报名将于 11 月 5 日 17:00 截止 [#2]。", res.getData().answer());
            assertEquals(List.of(2), res.getData().verifiedCitations());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractEndpointForwardsMultipartAndTranslatesScannedPdfError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/extract", exchange -> {
            byte[] body = "{\"detail\":\"该 PDF 未提取到有效文字（可能是扫描件或纯图片 PDF），当前版本暂不支持扫描件识别。\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(422, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var controller = new DocumentAssistantController(true,
                    "http://127.0.0.1:" + server.getAddress().getPort());
            var badExt = new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{1, 2, 3});
            assertThrows(BusinessException.class, () -> controller.extract(badExt));

            var scanPdf = new MockMultipartFile("file", "scan.pdf", "application/pdf", new byte[]{1, 2, 3});
            var ex = assertThrows(BusinessException.class, () -> controller.extract(scanPdf));
            assertTrue(ex.getMessage().contains("扫描件"));
        } finally {
            server.stop(0);
        }
    }
}
