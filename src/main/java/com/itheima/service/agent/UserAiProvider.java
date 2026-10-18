package com.itheima.service.agent;

import com.itheima.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Fixed official endpoints: request data cannot turn this service into an arbitrary URL proxy. */
public enum UserAiProvider {
    DEEPSEEK("deepseek","DeepSeek","https://api.deepseek.com","https://platform.deepseek.com/"),
    OPENAI("openai","OpenAI","https://api.openai.com","https://platform.openai.com/"),
    SILICONFLOW("siliconflow","硅基流动","https://api.siliconflow.cn","https://cloud.siliconflow.cn/");

    public final String id,name,baseUrl,consoleUrl;
    UserAiProvider(String id,String name,String baseUrl,String consoleUrl) {
        this.id=id; this.name=name; this.baseUrl=baseUrl; this.consoleUrl=consoleUrl;
    }
    public static UserAiProvider from(String id) {
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("请选择支持的 AI 服务商"));
    }
    public static List<Map<String,String>> publicOptions() {
        return Arrays.stream(values()).map(p -> Map.of("id",p.id,"name",p.name,
                "baseUrl",p.baseUrl+"/v1","consoleUrl",p.consoleUrl)).toList();
    }
}
