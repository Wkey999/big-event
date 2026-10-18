package com.itheima.service.agent;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.UserAiConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A new model client per ask. No server-key fallback, credential cache, database or shared defaults. */
@Component
public class UserAiClientFactory {
    private final RestClient.Builder restClient;

    public UserAiClientFactory() {
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var requests=new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(25));
        this.restClient=RestClient.builder().requestFactory(requests);
    }
    UserAiClientFactory(RestClient.Builder restClient) { this.restClient=restClient; }

    public ChatClient create(UserAiConfig config) {
        if(config==null || config.getApiKey()==null || !config.getApiKey().matches("[\\x21-\\x7E]{1,512}"))
            throw new BusinessException("请先配置你自己的 API Key");
        UserAiProvider provider=UserAiProvider.from(config.getProvider());
        if(config.getModel()==null || !config.getModel().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}"))
            throw new BusinessException("请填写有效的模型名称");
        var requests=restClient.clone().requestInterceptor((request,body,execution) -> {
            byte[] bounded=body;
            // DeepSeek thinking is on by default; this search uses non-thinking tool calls.
            if(provider==UserAiProvider.DEEPSEEK) {
                var json=new ObjectMapper();
                ObjectNode payload=(ObjectNode)json.readTree(body);
                payload.putObject("thinking").put("type","disabled");
                bounded=json.writeValueAsBytes(payload);
            }
            if(bounded.length>64_000) throw new BusinessException("检索上下文较大，请缩小关键词或时间范围后再试");
            request.getHeaders().setContentLength(bounded.length);
            return execution.execute(request,bounded);
        });
        var api=OpenAiApi.builder().baseUrl(provider.baseUrl).apiKey(config.getApiKey())
                .restClientBuilder(requests).responseErrorHandler(new SafeProviderErrors()).build();
        var optionsBuilder=OpenAiChatOptions.builder().model(config.getModel()).parallelToolCalls(false);
        if(provider==UserAiProvider.OPENAI) optionsBuilder.maxCompletionTokens(1024);
        else optionsBuilder.maxTokens(1024);
        var options=optionsBuilder.build();
        var model=OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .toolCallingManager(new LimitedToolCalls()).build();
        return ChatClient.builder(model).build();
    }

    /** Never read or log upstream error bodies, which can echo request credentials. */
    private static class SafeProviderErrors implements ResponseErrorHandler {
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return !response.getStatusCode().is2xxSuccessful();
        }
        public void handleError(ClientHttpResponse response) throws IOException {
            int status=response.getStatusCode().value();
            String message=switch(status) {
                case 401,403 -> "API Key 无效或没有模型权限，请检查你的配置";
                case 402 -> "你的模型账户额度不足，请到服务商控制台检查";
                case 429 -> "你的模型服务请求过于频繁或额度不足，请稍后重试";
                case 400,404 -> "模型名称或接口参数不受支持，请选择支持工具调用的对话模型";
                default -> "你的模型服务暂时不可用，请稍后再试";
            };
            throw new BusinessException(message);
        }
    }

    private static class LimitedToolCalls implements ToolCallingManager {
        private final ToolCallingManager delegate=ToolCallingManager.builder().build();
        private final AtomicInteger rounds=new AtomicInteger();
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
            return delegate.resolveToolDefinitions(options);
        }
        public ToolExecutionResult executeToolCalls(Prompt prompt,ChatResponse response) {
            if(rounds.incrementAndGet()>3) throw new BusinessException("本次检索步骤较多，请缩小问题范围后再试");
            if(response.getResults().stream().mapToInt(r -> r.getOutput().getToolCalls().size()).sum()>4)
                throw new BusinessException("本次检索工具请求过多，请缩小问题范围后再试");
            return delegate.executeToolCalls(prompt,response);
        }
    }
}
