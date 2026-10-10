package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.pojo.UserProfile;
import com.itheima.service.UserProfileService;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户偏好画像（阶段 B'）
 *
 * 画像本身是推荐重排的输入，前端不一定要消费；这两个接口主要用于：
 * 1) 出口检查——看聚合结果到底有没有信息量；2) 演示与调试；3) 阶段 D 的 tool 可直接复用 get()。
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    /** GET /profile/me 我的画像（从未生成过返回 data=null，前端据此走冷启动文案） */
    @GetMapping("/me")
    public Result<UserProfile> me() {
        return Result.success(userProfileService.get(ThreadLocalUtils.getUserId()));
    }

    /** POST /profile/refresh 立即重算我的画像（每日凌晨定时任务也会自动跑） */
    @PostMapping("/refresh")
    public Result<UserProfile> refresh() {
        return Result.success(userProfileService.generate(ThreadLocalUtils.getUserId()));
    }
}
