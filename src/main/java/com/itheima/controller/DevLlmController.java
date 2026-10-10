package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.service.LlmChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Development-only connectivity probe. It remains protected by the normal JWT interceptor. */
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Validated
@Profile("dev")
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
public class DevLlmController {

    private final LlmChatService llmChatService;

    @GetMapping("/echo")
    public Result<String> echo(@RequestParam("q") @NotBlank @Size(max = 200) String question) {
        return Result.success(llmChatService.echo(question));
    }
}
