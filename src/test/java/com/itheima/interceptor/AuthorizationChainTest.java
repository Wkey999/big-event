package com.itheima.interceptor;

import com.itheima.controller.AdminArticleController;
import com.itheima.mapper.UserMapper;
import com.itheima.pojo.PageBean;
import com.itheima.pojo.User;
import com.itheima.service.AdminArticleService;
import com.itheima.service.security.AccountAccessCache;
import com.itheima.service.security.TokenRevocationService;
import com.itheima.utils.JwtUtils;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthorizationChainTest {
    @AfterEach void clearContext() { ThreadLocalUtils.clear(); }
    @Test void realAdminRouteRetains401403200MatrixAndReusesAuthorizationSnapshot() throws Exception {
        var mapper=mock(UserMapper.class);
        var tokens=mock(JwtUtils.class);
        var service=mock(AdminArticleService.class);
        var ordinary=new User(); ordinary.setStatus(1); ordinary.setLoginEnabled(1); ordinary.setRole(0);
        var administrator=new User(); administrator.setStatus(1); administrator.setLoginEnabled(1); administrator.setRole(1);
        when(tokens.getUserId("ordinary")).thenReturn(7L);
        when(tokens.getUserId("administrator")).thenReturn(8L);
        when(mapper.findById(7L)).thenReturn(ordinary);
        when(mapper.findById(8L)).thenReturn(administrator);
        when(service.list(1,10,null,null,null,null)).thenReturn(new PageBean<>(0L,java.util.List.of()));
        var cache=new AccountAccessCache(mapper);
        var mvc=MockMvcBuilders.standaloneSetup(new AdminArticleController(service))
                .addInterceptors(new JwtInterceptor(tokens,cache,mock(TokenRevocationService.class)),new AdminInterceptor(cache)).build();
        mvc.perform(get("/admin/article/list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/article/list").header("Authorization","Bearer ordinary")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        for(int i=0;i<2;i++) mvc.perform(get("/admin/article/list").header("Authorization","Bearer administrator"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(mapper,times(1)).findById(8L);
        verify(service,times(2)).list(1,10,null,null,null,null);
    }
}
