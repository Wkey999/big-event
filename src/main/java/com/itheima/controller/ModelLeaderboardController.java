package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.service.leaderboard.ModelLeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/leaderboard")
public class ModelLeaderboardController {
    private final ModelLeaderboardService service;

    @GetMapping("/models")
    public Result<ModelLeaderboardService.Board> models(@RequestParam(defaultValue = "text") String dimension) {
        return Result.success(service.get(dimension));
    }
}
