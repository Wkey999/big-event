package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.pojo.User;
import com.itheima.pojo.UserLoginDTO;
import com.itheima.pojo.UserRegisterDTO;
import com.itheima.service.UserService;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户模块控制器
 * 提供注册、登录、查询/修改个人信息、修改密码等接口
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     * POST /user/register
     * 请求体: {"username":"xxx", "password":"xxx", "rePassword":"xxx"}
     * 不需要登录即可访问
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    /**
     * 用户登录
     * POST /user/login
     * 请求体: {"username":"xxx", "password":"xxx"}
     * 返回: {"token": "JWT令牌字符串"}
     * 不需要登录即可访问
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody UserLoginDTO dto) {
        String token = userService.login(dto);
        return Result.success(Map.of("token", token));
    }

    /**
     * 获取当前登录用户信息
     * GET /user/userInfo
     * 请求头: Authorization: Bearer {token}
     * 返回用户基本信息（密码字段已置空）
     */
    @GetMapping("/userInfo")
    public Result<User> getUserInfo() {
        Long userId = ThreadLocalUtils.getUserId();
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * 修改当前用户个人信息
     * PUT /user/update
     * 请求体: {"nickname":"新昵称", "email":"新邮箱", "avatar":"头像URL"}
     * 只能修改自己的信息，ID从token中获取
     */
    @PutMapping("/update")
    public Result<Void> updateUserInfo(@RequestBody User user) {
        user.setId(ThreadLocalUtils.getUserId());
        userService.updateUserInfo(user);
        return Result.success();
    }

    /**
     * 修改当前用户密码
     * PUT /user/updatePwd
     * 请求体: {"old_pwd":"旧密码", "new_pwd":"新密码"}
     * 会先验证旧密码是否正确
     */
    @PutMapping("/updatePwd")
    public Result<Void> updatePassword(@RequestBody Map<String, String> params) {
        Long userId = ThreadLocalUtils.getUserId();
        userService.updatePassword(userId, params.get("old_pwd"), params.get("new_pwd"));
        return Result.success();
    }
}
