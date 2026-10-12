package com.itheima.service;

import com.itheima.pojo.AdminUserRow;
import com.itheima.pojo.PageBean;

public interface AdminUserService {
    PageBean<AdminUserRow> list(Integer pageNum, Integer pageSize, String keyword, Integer role, Integer userType);

    void updateStatus(Long targetId, Integer status);

    void updateRole(Long targetId, Integer role);
}
