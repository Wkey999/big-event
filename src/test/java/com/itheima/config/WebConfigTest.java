package com.itheima.config;

import com.itheima.interceptor.AdminInterceptor;
import com.itheima.interceptor.JwtInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebConfigTest {
    static class ExposedRegistry extends InterceptorRegistry {
        List<Object> interceptors() { return super.getInterceptors(); }
    }
    @Test void privateServerKeyProbeAlwaysRequiresAdminEvenWhenExplicitlyEnabled() {
        var admin=mock(AdminInterceptor.class); var registry=new ExposedRegistry();
        new WebConfig(mock(JwtInterceptor.class),admin).addInterceptors(registry);
        var guarded=registry.interceptors().stream().filter(i -> i instanceof MappedInterceptor)
            .map(i -> (MappedInterceptor)i).filter(i -> i.getInterceptor()==admin).findFirst().orElseThrow();
        assertThat(guarded.getIncludePathPatterns()).contains("/admin/**","/dev/**");
    }

    @Test void leaderboardIsPublicButOtherApiRoutesStillRequireLogin() {
        var jwt = mock(JwtInterceptor.class);
        var registry = new ExposedRegistry();
        new WebConfig(jwt, mock(AdminInterceptor.class)).addInterceptors(registry);
        var guarded = registry.interceptors().stream().filter(i -> i instanceof MappedInterceptor)
            .map(i -> (MappedInterceptor)i).filter(i -> i.getInterceptor() == jwt).findFirst().orElseThrow();
        assertThat(guarded.getExcludePathPatterns()).contains("/leaderboard/models");
        assertThat(guarded.getExcludePathPatterns()).doesNotContain("/admin/**", "/article/list");
    }
}
