package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminSearchStats {
    private Long total;
    private Double zeroResultRate;
    private Double averageElapsedMs;
    private List<AdminKeywordStat> topKeywords;
    private List<AdminDailyStat> dailyTrend;
}
