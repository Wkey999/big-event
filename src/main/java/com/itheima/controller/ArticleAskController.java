package com.itheima.controller;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.AskDTO;
import com.itheima.pojo.AskResponse;
import com.itheima.pojo.Result;
import com.itheima.service.AskLogService;
import com.itheima.service.agent.AgentAskService;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.ObjectProvider;

/** Only available when a local LLM configuration explicitly enables the Agent. */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleAskController {

    private final ObjectProvider<AgentAskService> agentAskServiceProvider;
    private final ObjectProvider<AskLogService> askLogServiceProvider;

    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody AskDTO dto) {
        AgentAskService agentAskService = agentAskServiceProvider.getIfAvailable();
        if (agentAskService == null) {
            AskLogService askLogService = askLogServiceProvider.getIfAvailable();
            if (askLogService != null) {
                askLogService.record(ThreadLocalUtils.getUserId(), dto.getQuestion(), 0, 0, 0, false);
            }
            throw new BusinessException("智能问答未启用");
        }
        return Result.success(agentAskService.ask(dto.getQuestion()));
    }
}
