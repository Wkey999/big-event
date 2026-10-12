package com.itheima.service;

import com.itheima.pojo.AdminAskStats;
import com.itheima.pojo.AdminOverviewStats;
import com.itheima.pojo.AdminSearchStats;

public interface AdminStatsService {
    AdminOverviewStats overview();

    AdminSearchStats search(Integer days);

    AdminAskStats ask(Integer days);
}
