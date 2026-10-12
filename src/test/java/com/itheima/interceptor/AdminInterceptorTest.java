package com.itheima.interceptor;

import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInterceptorTest {

    @Mock
    private UserMapper userMapper;

    private AdminInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminInterceptor(userMapper);
        ThreadLocalUtils.setUserId(9L);
        User ordinary = new User();
        ordinary.setId(9L);
        ordinary.setRole(0);
        ordinary.setStatus(1);
        ordinary.setLoginEnabled(1);
        when(userMapper.findById(9L)).thenReturn(ordinary);
    }

    @AfterEach
    void clearContext() {
        ThreadLocalUtils.clear();
    }

    @Test
    void ordinaryUserGetsTheSameForbiddenResponseForExistingAndMissingIds() throws Exception {
        MockHttpServletRequest existingRequest = new MockHttpServletRequest("GET", "/admin/article/1");
        MockHttpServletResponse existingResponse = new MockHttpServletResponse();
        MockHttpServletRequest missingRequest = new MockHttpServletRequest("GET", "/admin/article/999999");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(existingRequest, existingResponse, new Object())).isFalse();
        assertThat(interceptor.preHandle(missingRequest, missingResponse, new Object())).isFalse();
        assertThat(existingResponse.getStatus()).isEqualTo(403);
        assertThat(missingResponse.getStatus()).isEqualTo(403);
        assertThat(existingResponse.getContentAsString()).isEqualTo(missingResponse.getContentAsString());
    }
}
