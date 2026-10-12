package com.itheima.service.impl;

import com.itheima.mapper.AdminMapper;
import com.itheima.pojo.AdminAskStats;
import com.itheima.pojo.AdminOverviewStats;
import com.itheima.pojo.AdminSearchStats;
import com.itheima.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int TOP_KEYWORD_LIMIT = 10;

    private final AdminMapper adminMapper;

    @Override
    public AdminOverviewStats overview() {
        return new AdminOverviewStats(
                zero(adminMapper.countUsers()),
                zero(adminMapper.countArticles()),
                zero(adminMapper.countDraftArticles()),
                zero(adminMapper.countPublishedArticles()),
                zero(adminMapper.countTodayArticles()));
    }

    @Override
    public AdminSearchStats search(Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(window(days));
        return new AdminSearchStats(
                zero(adminMapper.countSearchesSince(since)),
                zero(adminMapper.searchZeroResultRateSince(since)),
                zero(adminMapper.averageSearchElapsedSince(since)),
                safe(adminMapper.topSearchKeywordsSince(since, TOP_KEYWORD_LIMIT)),
                safe(adminMapper.searchDailyTrendSince(since)));
    }

    @Override
    public AdminAskStats ask(Integer days) {
        LocalDateTime since = LocalDateTime.now().minusDays(window(days));
        return new AdminAskStats(
                zero(adminMapper.countAsksSince(since)),
                zero(adminMapper.averageAskElapsedSince(since)),
                zero(adminMapper.averageAskReferencesSince(since)),
                zero(adminMapper.askSuccessRateSince(since)),
                safe(adminMapper.askDailyTrendSince(since)));
    }

    private static int window(Integer days) {
        return days == null ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
    }

    private static Long zero(Long value) {
        return value == null ? 0L : value;
    }

    private static Double zero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
