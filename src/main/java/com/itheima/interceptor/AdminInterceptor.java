package com.itheima.interceptor;

import com.itheima.mapper.UserMapper;
import com.itheima.pojo.User;
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

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = ThreadLocalUtils.getUserId();
        User user = userId == null ? null : userMapper.findById(userId);
        if (user != null
                && Integer.valueOf(1).equals(user.getRole())
                && Integer.valueOf(1).equals(user.getStatus())
                && Integer.valueOf(1).equals(user.getLoginEnabled())) {
            return true;
        }

        // Do not inspect the requested resource here: every authenticated non-admin gets the same 403.
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(FORBIDDEN_BODY);
        return false;
    }
}
