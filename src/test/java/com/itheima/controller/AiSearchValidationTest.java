package com.itheima.controller;

import com.itheima.config.GlobalExceptionHandler;
import com.itheima.pojo.AskResponse;
import com.itheima.service.AskLogService;
import com.itheima.service.agent.AgentAskService;
import com.itheima.service.agent.AiSearchClientRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class AiSearchValidationTest {
    final AgentAskService service=mock(AgentAskService.class);
    MockMvc mvc;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        ObjectProvider<AgentAskService> agent=mock(ObjectProvider.class);
        ObjectProvider<AskLogService> logs=mock(ObjectProvider.class);
        ObjectProvider<AiSearchClientRouter> router=mock(ObjectProvider.class);
        when(agent.getIfAvailable()).thenReturn(service);
        mvc=MockMvcBuilders.standaloneSetup(new ArticleAskController(agent,logs,router)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void rejectsInvalidModeBeforeCallingModel() throws Exception {
        mvc.perform(post("/article/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hello\",\"mode\":\"other\"}"))
            .andExpect(jsonPath("$.code").value(1)); verifyNoInteractions(service);
    }
    @Test void validatesNestedConfigAndNeverEchoesCredentials() throws Exception {
        mvc.perform(post("/article/ask").contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"hello\",\"mode\":\"custom\",\"aiConfig\":{\"provider\":\"evil\",\"model\":\"test\",\"apiKey\":\"fake-secret-test\"}}"))
            .andExpect(jsonPath("$.code").value(1)).andExpect(content().string(not(containsString("fake-secret-test"))));
        verifyNoInteractions(service);
    }
    @Test void malformedJsonDoesNotEchoSecret() throws Exception {
        mvc.perform(post("/article/ask").contentType(MediaType.APPLICATION_JSON).content("{\"aiConfig\":{\"apiKey\":\"fake-secret-test\"},invalid}"))
            .andExpect(jsonPath("$.code").value(1)).andExpect(content().string(not(containsString("fake-secret-test"))));
        verifyNoInteractions(service);
    }
    @Test void explicitPlatformRequestDoesNotRequirePersonalKey() throws Exception {
        when(service.ask("hello","platform",null)).thenReturn(new AskResponse("answer",List.of()));
        mvc.perform(post("/article/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hello\",\"mode\":\"platform\"}"))
            .andExpect(jsonPath("$.code").value(0)); verify(service).ask("hello","platform",null);
    }
    @Test void providerListContainsPublicMetadataOnly() throws Exception {
        mvc.perform(get("/article/ai/providers")).andExpect(jsonPath("$.data",hasSize(3)))
            .andExpect(jsonPath("$.data[0].apiKey").doesNotExist()).andExpect(jsonPath("$.data[0].id").value("deepseek"));
    }
}
