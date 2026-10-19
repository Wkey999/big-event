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
        int completed=0,skipped=0,failed=0;
        for(var source:ContentFeedSource.values()) {
            try {
                var run=service.collect(source.key);
                if(run.isSkipped()) { skipped++; log.info("资讯采集跳过 source={}, message={}",source.key,run.getMessage()); }
                else if(run.isSuccess()) { completed++; log.info("资讯采集完成 source={}, scanned={}, inserted={}",source.key,run.getScanned(),run.getInserted()); }
                else { failed++; log.warn("资讯采集失败 source={}, message={}",source.key,run.getMessage()); }
            }
            catch(Exception e) { failed++; log.warn("资讯采集未完成 source={}, type={}",source.key,e.getClass().getSimpleName()); }
        }
        log.info("资讯采集批次 completed={}, skipped={}, failed={}",completed,skipped,failed);
    }
}
