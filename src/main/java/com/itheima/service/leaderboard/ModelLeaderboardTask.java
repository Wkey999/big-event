package com.itheima.service.leaderboard;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModelLeaderboardTask {
    private final ModelLeaderboardService service;

    @Value("${app.leaderboard.sync-enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${app.leaderboard.sync-cron:0 20 4 * * *}",
            zone = "${app.leaderboard.time-zone:Asia/Shanghai}")
    public void dailySync() {
        if (enabled) service.syncAll();
    }

    @Scheduled(fixedDelayString = "${app.leaderboard.retry-check-ms:300000}",
            initialDelayString = "${app.leaderboard.retry-initial-delay-ms:60000}")
    public void bootstrapAndRetry() {
        if (enabled) service.retryFailedToday();
    }
}
