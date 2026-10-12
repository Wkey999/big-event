package com.itheima.controller;

import com.itheima.pojo.AdminUserRoleDTO;
import com.itheima.pojo.AdminUserRow;
import com.itheima.pojo.AdminUserStatusDTO;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.Result;
import com.itheima.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/list")
    public Result<PageBean<AdminUserRow>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer role,
                                                @RequestParam(required = false) Integer userType) {
        return Result.success(adminUserService.list(pageNum, pageSize, keyword, role, userType));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody AdminUserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody AdminUserRoleDTO dto) {
        adminUserService.updateRole(id, dto.getRole());
        return Result.success();
    }
}
