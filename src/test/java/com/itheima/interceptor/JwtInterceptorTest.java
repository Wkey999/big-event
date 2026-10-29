package com.itheima.interceptor;

import com.itheima.utils.JwtUtils;
import com.itheima.utils.ThreadLocalUtils;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
import com.itheima.service.security.AccountAccessCache;
import com.itheima.service.security.TokenRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final TokenRevocationService tokenRevocation = mock(TokenRevocationService.class);
    private final JwtInterceptor interceptor = new JwtInterceptor(jwtUtils, new AccountAccessCache(userMapper), tokenRevocation);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void repeatedRequestsUseOneLookupAndExpiredRevokedAccountReturns401(boolean deleted) throws Exception {
        var millis=new java.util.concurrent.atomic.AtomicLong(1000);
        java.time.Clock clock=new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(millis.get()); }
        };
        var cache=new AccountAccessCache(userMapper,clock);
        var jwt=new JwtInterceptor(jwtUtils,cache,tokenRevocation);
        var admin=new AdminInterceptor(cache);
        var user=new User(); user.setStatus(1); user.setLoginEnabled(1); user.setRole(1);
        when(jwtUtils.getUserId("token")).thenReturn(12L); when(userMapper.findById(12L)).thenReturn(user);
        for(int i=0;i<2;i++) {
            var request=new MockHttpServletRequest("GET","/admin/articles"); request.addHeader("Authorization","Bearer token");
            assertThat(jwt.preHandle(request,new MockHttpServletResponse(),new Object())).isTrue();
            assertThat(admin.preHandle(request,new MockHttpServletResponse(),new Object())).isTrue();
            jwt.afterCompletion(request,new MockHttpServletResponse(),new Object(),null);
        }
        org.mockito.Mockito.verify(userMapper,org.mockito.Mockito.times(1)).findById(12L);
        if(deleted) user.setDeleted(1); else user.setStatus(0);
        millis.addAndGet(20_001);
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer token");
        var response=new MockHttpServletResponse();
        assertThat(jwt.preHandle(request,response,new Object())).isFalse(); assertThat(response.getStatus()).isEqualTo(401);
        org.mockito.Mockito.verify(userMapper,org.mockito.Mockito.times(2)).findById(12L);
    }

    @Test
    void disabledAccountCannotKeepUsingAnIssuedToken() throws Exception {
        when(jwtUtils.getUserId("issued-token")).thenReturn(12L);
        User user = new User();
        user.setStatus(0);
        user.setLoginEnabled(1);
        when(userMapper.findById(12L)).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/article/add");
        request.addHeader("Authorization", "Bearer issued-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(ThreadLocalUtils.getUserId()).isNull();
    }

    @Test
    void revokedTokenIsRejectedBeforeAccountLookup() throws Exception {
        when(jwtUtils.getUserId("issued-token")).thenReturn(12L);
        when(tokenRevocation.isRevoked("issued-token")).thenReturn(true);
        var request = new MockHttpServletRequest("GET", "/user/userInfo");
        request.addHeader("Authorization", "Bearer issued-token");
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(ThreadLocalUtils.getUserId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(userMapper);
    }

    @Test
    void redisFailureReturns503InsteadOfTreatingTokenAsValidOrInvalid() throws Exception {
        when(jwtUtils.getUserId("issued-token")).thenReturn(12L);
        when(tokenRevocation.isRevoked("issued-token"))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"));
        var request = new MockHttpServletRequest("GET", "/user/userInfo");
        request.addHeader("Authorization", "Bearer issued-token");
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(ThreadLocalUtils.getUserId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(userMapper);
    }

    @Test
    void deletedAccountCannotKeepUsingAnIssuedToken() throws Exception {
        when(jwtUtils.getUserId("issued-token")).thenReturn(12L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/article/mine");
        request.addHeader("Authorization", "Bearer issued-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

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
        User user = new User();
        user.setStatus(1);
        user.setLoginEnabled(1);
        when(userMapper.findById(12L)).thenReturn(user);
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
