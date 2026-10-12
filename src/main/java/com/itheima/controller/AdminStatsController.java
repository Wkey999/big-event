package com.itheima.controller;

import com.itheima.pojo.AdminAskStats;
import com.itheima.pojo.AdminOverviewStats;
import com.itheima.pojo.AdminSearchStats;
import com.itheima.pojo.Result;
import com.itheima.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public Result<AdminOverviewStats> overview() {
        return Result.success(adminStatsService.overview());
    }

    @GetMapping("/search")
    public Result<AdminSearchStats> search(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(adminStatsService.search(days));
    }

    @GetMapping("/ask")
    public Result<AdminAskStats> ask(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(adminStatsService.ask(days));
    }
}
