package com.itheima.interceptor;

import com.itheima.service.security.AccountAccessCache;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** A uniform role gate for every additive /admin/** endpoint. JwtInterceptor runs first. */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private static final String FORBIDDEN_BODY = "{\"code\":403,\"message\":\"需要管理员权限\"}";

    private final AccountAccessCache accountCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = ThreadLocalUtils.getUserId();
        Object authenticated=request.getAttribute(AccountAccessCache.REQUEST_ATTRIBUTE);
        var account=authenticated instanceof AccountAccessCache.Account value && value.userId().equals(userId)
                ? value : accountCache.get(userId);
        if (account != null && account.active() && account.admin()) {
            return true;
        }

        // Do not inspect the requested resource here: every authenticated non-admin gets the same 403.
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(FORBIDDEN_BODY);
        return false;
    }
}
