package com.itheima.interceptor;

import com.itheima.utils.JwtUtils;
import com.itheima.utils.ThreadLocalUtils;
import com.itheima.service.security.AccountAccessCache;
import com.itheima.service.security.TokenRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final AccountAccessCache accountCache;
    private final TokenRevocationService tokenRevocation;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        ThreadLocalUtils.clear();
        request.removeAttribute(AccountAccessCache.REQUEST_ATTRIBUTE);
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return false;
        }

        try {
            token = token.substring(7);
            Long userId = jwtUtils.getUserId(token);
            try {
                if (tokenRevocation.isRevoked(token)) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"登录已退出，请重新登录\"}");
                    return false;
                }
            } catch (RuntimeException e) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":503,\"message\":\"登录状态暂时无法验证，请稍后重试\"}");
                return false;
            }
            var account=accountCache.get(userId);
            if (account == null || !account.active()) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"账号不可用，请重新登录\"}");
                return false;
            }
            ThreadLocalUtils.setUserId(userId);
            request.setAttribute(AccountAccessCache.REQUEST_ATTRIBUTE,account);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ThreadLocalUtils.clear();
        request.removeAttribute(AccountAccessCache.REQUEST_ATTRIBUTE);
    }
}
