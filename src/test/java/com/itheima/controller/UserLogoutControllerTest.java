package com.itheima.controller;

import com.itheima.service.UserService;
import com.itheima.service.security.AccountAccessCache;
import com.itheima.service.security.TokenRevocationService;
import com.itheima.interceptor.JwtInterceptor;
import com.itheima.utils.JwtUtils;
import com.itheima.utils.OssUtils;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserLogoutControllerTest {
    @AfterEach void clearContext() { ThreadLocalUtils.clear(); }

    @Test
    void logoutRevokesThePresentedTokenAndNextAuthenticatedRequestIsRejected() throws Exception {
        var jwt = mock(JwtUtils.class);
        var accountCache = mock(AccountAccessCache.class);
        var revocation = mock(TokenRevocationService.class);
        var userService = mock(UserService.class);
        var revoked = new AtomicBoolean();
        when(jwt.getUserId("token")).thenReturn(7L);
        when(accountCache.get(7L)).thenReturn(new AccountAccessCache.Account(7L, true, false));
        when(revocation.isRevoked("token")).thenAnswer(call -> revoked.get());
        doAnswer(call -> { revoked.set(true); return null; }).when(revocation).revoke("token");

        var mvc = MockMvcBuilders.standaloneSetup(
                new UserController(userService, mock(OssUtils.class), revocation))
                .addInterceptors(new JwtInterceptor(jwt, accountCache, revocation)).build();

        mvc.perform(post("/user/logout").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/user/userInfo").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(revocation).revoke("token");
        verifyNoInteractions(userService);
    }
}
