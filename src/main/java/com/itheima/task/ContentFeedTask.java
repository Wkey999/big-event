package com.itheima.task;
import com.itheima.config.ContentFeedProperties;
import com.itheima.service.feed.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentFeedTask {
    private final ContentFeedProperties properties;
    private final ContentFeedService service;
    @Scheduled(fixedDelayString="${app.content-feeds.interval-ms:1800000}",initialDelayString="${app.content-feeds.initial-delay-ms:60000}")
    public void collect() {
        if(!properties.isEnabled()) return;
        for(var source:ContentFeedSource.values()) {
            try { service.collect(source.key); }
            catch(Exception e) { log.warn("资讯采集未完成 source={}, type={}",source.key,e.getClass().getSimpleName()); }
        }
    }
}
