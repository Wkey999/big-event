package com.itheima.controller;

import com.itheima.pojo.BrowseLogDTO;
import com.itheima.pojo.Result;
import com.itheima.service.BrowseLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 行为采集（阶段 A 数据闭环）
 *
 * 前端在「卡片进入视口（曝光，dwellMs=0）」和「预览弹窗关闭（真实停留时长）」时静默上报。
 * 这是全站写入最频繁的端点，因此刻意保持最短路径：一次校验 + 一次去重查询 + 一次写。
 * 高并发下应改为 Redis 缓冲 + 批量落库——个人项目量级不需要，但面试会问，故记于此。
 */
@RestController
@RequestMapping("/article")
@RequiredArgsConstructor
public class BrowseLogController {

    private final BrowseLogService browseLogService;

    /**
     * POST /article/browse
     * body: {"articleId":1,"dwellMs":0}
     */
    @PostMapping("/browse")
    public Result<Void> browse(@Valid @RequestBody BrowseLogDTO dto) {
        browseLogService.report(dto);
        return Result.success();
    }
}
