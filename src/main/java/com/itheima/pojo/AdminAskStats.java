package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminAskStats {
    private Long total;
    private Double averageElapsedMs;
    private Double averageReferenceCount;
    private Double successRate;
    private List<AdminDailyStat> dailyTrend;
}
