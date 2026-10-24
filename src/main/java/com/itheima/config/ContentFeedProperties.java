package com.itheima.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="app.content-feeds")
public class ContentFeedProperties {
    private boolean enabled=false;
    private int maxNewPerSource=5;
    private int maxAgeDays=30;
    private int maxNewPerDay=60;
    private int maxNewPerSourcePerDay=10;
    private boolean autoPublishEnabled=false;
    private java.util.Set<String> autoPublishSources=java.util.Set.of("github","deepseek_github","qwen_github");
}
