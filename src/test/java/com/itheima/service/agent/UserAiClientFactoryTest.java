package com.itheima.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.exception.BusinessException;
import com.itheima.pojo.UserAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UserAiClientFactoryTest {
    public static class Lookup {
        int calls;
        @org.springframework.ai.tool.annotation.Tool(name="test_lookup",description="Return a test result")
        public String lookup() { calls++; return "result"; }
    }
    static UserAiConfig config(String provider,String key) {
        var config=new UserAiConfig(); config.setProvider(provider); config.setModel("test-model"); config.setApiKey(key); return config;
    }
    private static final String ANSWER="""
        {"id":"test","object":"chat.completion","created":1,"model":"test-model",
        "choices":[{"index":0,"message":{"role":"assistant","content":"found"},"finish_reason":"stop"}],
        "usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}
        """;

    @Test void eachRequestUsesOnlyItsOwnCredentialsAndFixedProvider() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var factory=new UserAiClientFactory(builder);
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
            .andExpect(header("Authorization","Bearer fake-first"))
            .andExpect(jsonPath("$.model").value("test-model"))
            .andExpect(jsonPath("$.thinking.type").value("disabled"))
            .andExpect(jsonPath("$.max_tokens").value(1024))
            .andRespond(withSuccess(ANSWER,MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(header("Authorization","Bearer fake-second"))
            .andExpect(jsonPath("$.thinking").doesNotExist())
            .andExpect(jsonPath("$.max_completion_tokens").value(1024))
            .andRespond(withSuccess(ANSWER,MediaType.APPLICATION_JSON));
        var first=factory.create(config("deepseek","fake-first"));
        var second=factory.create(config("openai","fake-second"));
        assertThat(first.prompt().user("hello").call().content()).isEqualTo("found");
        assertThat(second.prompt().user("hello").call().content()).isEqualTo("found");
        server.verify();
    }
    @Test void invalidKeyDoesNotRetryOrExposeUpstreamErrorBody() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"sensitive-provider-echo-fake-secret\"}"));
        assertThatThrownBy(() -> new UserAiClientFactory(builder).create(config("deepseek","fake-secret"))
            .prompt().user("hello").call().content()).isInstanceOf(BusinessException.class)
            .hasMessageContaining("API Key 无效").hasMessageNotContaining("fake-secret");
        server.verify();
    }
    @Test void missingConfigurationAndUnknownProvidersNeverUseServerCredentials() {
        var factory=new UserAiClientFactory();
        assertThatThrownBy(() -> factory.create(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.create(config("http://127.0.0.1","fake-key"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.create(config("deepseek","fake\r\nkey"))).isInstanceOf(BusinessException.class);
    }
    @Test void keyIsWriteOnlyInJsonAndExcludedFromToString() throws Exception {
        var mapper=new ObjectMapper(); var config=config("deepseek","fake-secret");
        assertThat(mapper.writeValueAsString(config)).doesNotContain("fake-secret","apiKey");
        assertThat(config.toString()).doesNotContain("fake-secret");
        assertThat(mapper.readValue("{\"apiKey\":\"fake-secret\"}",UserAiConfig.class).getApiKey()).isEqualTo("fake-secret");
    }
    @Test void repeatedToolCallsStopAfterFourModelRequestsAndThreeToolRounds() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        String toolResponse="""
            {"id":"test","object":"chat.completion","created":1,"model":"test-model",
            "choices":[{"index":0,"message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"call-test","type":"function","function":{"name":"test_lookup","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}
            """;
        for(int i=0;i<4;i++) server.expect(requestTo("https://api.deepseek.com/v1/chat/completions"))
            .andRespond(withSuccess(toolResponse,MediaType.APPLICATION_JSON));
        var lookup=new Lookup();
        assertThatThrownBy(() -> new UserAiClientFactory(builder).create(config("deepseek","fake-key"))
            .prompt().user("search").tools(lookup).call().content()).isInstanceOf(BusinessException.class).hasMessageContaining("步骤较多");
        assertThat(lookup.calls).isEqualTo(3); server.verify();
    }
    @Test void oversizedContextIsRejectedBeforeSendingToProvider() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        assertThatThrownBy(() -> new UserAiClientFactory(builder).create(config("deepseek","fake-key"))
            .prompt().user("x".repeat(65000)).call().content()).isInstanceOf(BusinessException.class).hasMessageContaining("上下文较大");
        server.verify();
    }
}
