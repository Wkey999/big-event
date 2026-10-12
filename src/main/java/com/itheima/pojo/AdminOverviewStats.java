package com.itheima.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminOverviewStats {
    private Long users;
    private Long articles;
    private Long drafts;
    private Long published;
    private Long todayNewArticles;
}
