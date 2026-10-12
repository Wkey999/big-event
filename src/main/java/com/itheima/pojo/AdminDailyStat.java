package com.itheima.pojo;

import lombok.Data;

@Data
public class AdminDailyStat {
    private String day;
    private Long count;
    private Double successRate;
}
