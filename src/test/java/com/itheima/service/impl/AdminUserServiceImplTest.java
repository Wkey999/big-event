package com.itheima.service.impl;

import com.itheima.exception.BusinessException;
import com.itheima.mapper.AdminMapper;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private UserMapper userMapper;

    @AfterEach
    void clearContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void cannotModifyOwnRole() {
        ThreadLocalUtils.setUserId(7L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(adminMapper, userMapper);

        assertThatThrownBy(() -> service.updateRole(7L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能修改自己的角色");
        verify(userMapper, never()).updateRole(7L, 0);
    }

    @Test
    void cannotDisableOwnAccount() {
        ThreadLocalUtils.setUserId(7L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(adminMapper, userMapper);

        assertThatThrownBy(() -> service.updateStatus(7L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能禁用自己的账号");
        verify(userMapper, never()).updateStatus(7L, 0);
    }

    @Test
    void cannotDemoteLastAdministrator() {
        ThreadLocalUtils.setUserId(99L);
        AdminUserServiceImpl service = new AdminUserServiceImpl(adminMapper, userMapper);
        User lastAdmin = new User();
        lastAdmin.setId(7L);
        lastAdmin.setRole(1);
        when(userMapper.lockActiveAdminIds()).thenReturn(List.of(7L));
        when(userMapper.findByIdForUpdate(7L)).thenReturn(lastAdmin);

        assertThatThrownBy(() -> service.updateRole(7L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能移除最后一个管理员");
        verify(userMapper, never()).updateRole(7L, 0);
    }
}
