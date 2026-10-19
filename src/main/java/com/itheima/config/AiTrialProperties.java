package com.itheima.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai-trial")
public class AiTrialProperties {
    private boolean enabled = false;
    private int dailyUserLimit = 3;
    private int dailySiteLimit = 100;
    private int dailyIpLimit = 10;
    private String model = "deepseek-flash";
    private String apiKey = "";

    @Override
    public String toString() { return "AiTrialProperties[credentials omitted]"; }
}
