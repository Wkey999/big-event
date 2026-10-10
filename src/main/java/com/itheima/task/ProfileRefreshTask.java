package com.itheima.task;

import com.itheima.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每日画像刷新（阶段 B'）
 *
 * 只处理「近 7 天有行为」的活跃用户：全表扫描在用户量上来后是纯浪费，
 * 而沉默用户的画像也不会因为刷新而变得更有信息量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileRefreshTask {

    private static final int WINDOW_DAYS = 7;

    private final UserProfileService userProfileService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshDaily() {
        List<Long> userIds = userProfileService.activeUserIds(WINDOW_DAYS);
        int ok = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                // 通过 Spring 代理调用，@Transactional 才会生效（同类内部 this.generate() 会绕过事务）
                userProfileService.generate(userId);
                ok++;
            } catch (Exception e) {
                // 单个用户失败不能影响整批：画像只是推荐输入，缺一个用户不该拖垮任务
                failed++;
                log.warn("生成画像失败 userId={}", userId, e);
            }
        }
        log.info("每日画像刷新完成：活跃用户 {} 个，成功 {}，失败 {}", userIds.size(), ok, failed);
        // 阶段 B 接入 LLM 后，这里要加「串行 + 固定间隔」的限流（示例：每用户间隔 200ms），
        // 并统计当日调用量做额度熔断；规则画像不消耗外部额度，故现在不加延迟。
    }
}
