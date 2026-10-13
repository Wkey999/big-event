package com.itheima.controller;

import com.itheima.pojo.FollowState;
import com.itheima.pojo.Result;
import com.itheima.service.FollowService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/follow/{userId}")
    public Result<FollowState> follow(@PathVariable Long userId) {
        return Result.success(followService.follow(userId, true));
    }

    @DeleteMapping("/follow/{userId}")
    public Result<FollowState> unfollow(@PathVariable Long userId) {
        return Result.success(followService.follow(userId, false));
    }

    @GetMapping("/user/{id}/follow")
    public Result<FollowState> state(@PathVariable Long id) {
        return Result.success(followService.state(id, ThreadLocalUtils.getUserId()));
    }
}
