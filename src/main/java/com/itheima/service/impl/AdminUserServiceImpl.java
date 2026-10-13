package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.AdminMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.AdminUserRow;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.User;
import com.itheima.service.AdminUserService;
import com.itheima.utils.LikeUtils;
import com.itheima.utils.ThreadLocalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;

    @Override
    public PageBean<AdminUserRow> list(Integer pageNum, Integer pageSize, String keyword,
                                       Integer role, Integer userType) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : Math.min(pageNum, 100_000);
        int safeSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        validateBinary(role, "用户角色");
        validateBinary(userType, "用户类型");
        String safeKeyword = keyword == null ? null : keyword.trim();
        if (safeKeyword != null && safeKeyword.length() > 100) {
            safeKeyword = safeKeyword.substring(0, 100);
        }
        String sqlKeyword = safeKeyword == null ? null : LikeUtils.escape(safeKeyword);
        Long total = adminMapper.countAdminUsers(sqlKeyword, role, userType);
        List<AdminUserRow> items = total == null || total == 0
                ? List.of()
                : adminMapper.findAdminUsers(sqlKeyword, role, userType,
                        pageOffset(safePage, safeSize), safeSize);
        return new PageBean<>(total == null ? 0L : total, items);
    }

    @Override
    @Transactional
    public void updateStatus(Long targetId, Integer status) {
        validateBinary(status, "用户状态");
        Long currentId = ThreadLocalUtils.getUserId();
        if (targetId.equals(currentId) && status == 0) {
            throw new BusinessException("不能禁用自己的账号");
        }
        assertCurrentAdmin();
        User target = userMapper.findByIdForUpdate(targetId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.updateStatus(targetId, status);
    }

    @Override
    @Transactional
    public void updateRole(Long targetId, Integer role) {
        validateBinary(role, "用户角色");
        Long currentId = ThreadLocalUtils.getUserId();
        if (targetId.equals(currentId)) {
            throw new BusinessException("不能修改自己的角色");
        }

        // A locking current-read serializes concurrent demotions of the last administrator.
        List<Long> adminIds = userMapper.lockActiveAdminIds();
        User target = userMapper.findByIdForUpdate(targetId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if (Integer.valueOf(1).equals(target.getRole()) && role == 0 && adminIds.size() <= 1) {
            throw new BusinessException("不能移除最后一个管理员");
        }
        if (!adminIds.contains(currentId)) {
            // The caller may have lost admin status after AdminInterceptor ran but before this transaction.
            throw new BusinessException("管理员权限已变更");
        }
        assertCurrentAdmin();
        userMapper.updateRole(targetId, role);
    }

    private void assertCurrentAdmin() {
        User current = userMapper.findByIdForUpdate(ThreadLocalUtils.getUserId());
        if (current == null || !Integer.valueOf(1).equals(current.getRole())
                || !Integer.valueOf(1).equals(current.getStatus())
                || !Integer.valueOf(1).equals(current.getLoginEnabled())) {
            throw new BusinessException("管理员权限已变更");
        }
    }

    private static void validateBinary(Integer value, String label) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException(label + "只能是0或1");
        }
    }

    private static int pageOffset(int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }
}
