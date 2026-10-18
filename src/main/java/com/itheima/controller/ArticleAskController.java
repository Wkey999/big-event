package com.itheima.controller;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.AskDTO;
import com.itheima.pojo.AskResponse;
import com.itheima.pojo.Result;
import com.itheima.service.AskLogService;
import com.itheima.service.agent.AgentAskService;
import com.itheima.service.agent.UserAiProvider;
import com.itheima.service.agent.AiSearchClientRouter;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** Always registered; reports a business error and records the failed ask when no Agent is configured. */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class ArticleAskController {

    private final ObjectProvider<AgentAskService> agentAskServiceProvider;
    private final ObjectProvider<AskLogService> askLogServiceProvider;
    private final ObjectProvider<AiSearchClientRouter> clientRouterProvider;
    @GetMapping("/ai/providers")
    public Result<List<Map<String,String>>> providers() { return Result.success(UserAiProvider.publicOptions()); }

    @GetMapping("/ai/status")
    public Result<AiSearchClientRouter.Status> status() {
        var router=clientRouterProvider.getIfAvailable();
        if(router==null) throw new BusinessException("AI 搜索暂未启用");
        return Result.success(router.status());
    }

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
        return Result.success(agentAskService.ask(dto.getQuestion(),dto.getMode(),dto.getAiConfig()));
    }
}
