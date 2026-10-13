package com.itheima.interceptor;

import com.itheima.utils.JwtUtils;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtils);

    @AfterEach
    void clearUser() {
        ThreadLocalUtils.clear();
    }

    @Test
    void communityReadWithoutLoginReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/article/12/comments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(ThreadLocalUtils.getUserId()).isNull();
    }

    @Test
    void communityReadWithBearerTokenUsesAuthenticatedUser() throws Exception {
        when(jwtUtils.getUserId("valid-token")).thenReturn(12L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/article/12/comments");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ThreadLocalUtils.getUserId()).isEqualTo(12L);
    }

    @Test
    void unknownPathWithoutLoginStillReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/no/such/path");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void communityWriteWithoutLoginStillReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/article/12/comment");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
