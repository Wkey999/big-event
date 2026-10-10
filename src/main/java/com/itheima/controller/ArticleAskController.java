package com.itheima.controller;

import com.itheima.pojo.AskDTO;
import com.itheima.pojo.AskResponse;
import com.itheima.pojo.Result;
import com.itheima.service.agent.AgentAskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only available when a local LLM configuration explicitly enables the Agent. */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
public class ArticleAskController {

    private final AgentAskService agentAskService;

    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody AskDTO dto) {
        return Result.success(agentAskService.ask(dto.getQuestion()));
    }
}
