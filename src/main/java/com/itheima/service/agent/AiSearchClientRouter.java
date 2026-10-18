package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.pojo.UserAiConfig;
import com.itheima.utils.ThreadLocalUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class AiSearchClientRouter {
    private final UserAiClientFactory factory;
    private final AiTrialQuotaService quota;
    private final AiTrialProperties properties;

    public AiSearchClientRouter(UserAiClientFactory factory, AiTrialQuotaService quota, AiTrialProperties properties) {
        this.factory=factory; this.quota=quota; this.properties=properties;
    }

    public ChatClient create(String mode,UserAiConfig config) {
        if("custom".equals(mode)) return factory.create(config);
        if(!"platform".equals(mode)) throw new BusinessException("请选择免费体验或自己的 API");
        if(config!=null) throw new BusinessException("免费体验模式不能携带个人 API 配置");
        if(!available()) throw new BusinessException("平台免费体验暂未开放，请填写自己的 API");
        UserAiConfig trial=new UserAiConfig();
        trial.setProvider("deepseek"); trial.setModel(properties.getModel()); trial.setApiKey(properties.getApiKey());
        ChatClient client=factory.create(trial);
        quota.reserve(ThreadLocalUtils.getUserId());
        return client;
    }

    public Status status() {
        return new Status(available(), "DeepSeek", properties.getModel(), quota.status(ThreadLocalUtils.getUserId()));
    }
    public String modelLabel(String mode,UserAiConfig config) {
        return "platform".equals(mode) ? "platform:"+properties.getModel()
                : config==null ? "custom:unconfigured" : "custom:"+config.getProvider()+"/"+config.getModel();
    }
    private boolean available() {
        return properties.isEnabled() && properties.getDailyUserLimit()>0 && properties.getDailySiteLimit()>0
                && properties.getApiKey()!=null && !properties.getApiKey().isBlank();
    }
    public record Status(boolean enabled,String provider,String model,AiTrialQuotaService.Quota quota) {}
}
