package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.pojo.User;
import com.itheima.pojo.UserLoginDTO;
import com.itheima.pojo.UserRegisterDTO;
import com.itheima.pojo.UserUpdateDTO;
import com.itheima.service.UserService;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 用户模块控制器
 * 提供注册、登录、查询/修改个人信息、修改密码等接口
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 文件上传保存目录，从 application.yml 读取 */
    @Value("${file.upload-path}")
    private String uploadPath;

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
     * 修改当前用户个人信息（带参数校验）
     * PUT /user/update
     * 请求体: {"nickname":"新昵称", "email":"新邮箱"}
     * nickname 和 email 会经过 @Valid 校验，不合法返回错误提示
     */
    @PutMapping("/update")
    public Result<Void> updateUserInfo(@Valid @RequestBody UserUpdateDTO dto) {
        User user = new User();
        user.setId(ThreadLocalUtils.getUserId());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        userService.updateUserInfo(user);
        return Result.success();
    }

    /**
     * 上传头像图片
     * POST /user/upload
     * 请求: form-data，字段名 file，值为图片文件
     * 返回: 图片的访问URL，前端拿到后再调 /user/update 更新头像字段
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam MultipartFile file) throws IOException {
        // 校验文件类型：只允许图片
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.matches("(?i).+\\.(jpg|jpeg|png|gif|webp)$")) {
            throw new RuntimeException("只允许上传图片文件(jpg/png/gif/webp)");
        }
        // 用UUID重命名，防止文件名冲突和路径注入
        String ext = originalName.substring(originalName.lastIndexOf("."));
        String newName = UUID.randomUUID().toString().replace("-", "") + ext;
        // 保存到本地磁盘
        File dest = new File(uploadPath + newName);
        dest.getParentFile().mkdirs();
        file.transferTo(dest);
        // 返回可通过浏览器访问的URL
        return Result.success("/uploads/" + newName);
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
