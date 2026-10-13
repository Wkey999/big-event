package com.itheima.controller;

import com.itheima.config.GlobalExceptionHandler;
import com.itheima.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class ArticleValidationResponseTest {

    @Test
    void validationFailureRemainsHttp200WithNonzeroCode() throws Exception {
        ArticleService articleService = mock(ArticleService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ArticleController(articleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/article/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)));
    }
}
