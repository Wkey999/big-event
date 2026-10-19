package com.itheima.service.security;

import com.itheima.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Use the socket peer. Untrusted X-Forwarded-For/Forwarded headers cannot mint quotas. */
@Component
public class ClientAddressResolver {
    public String current() {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return attributes.getRequest().getRemoteAddr();
        throw new BusinessException("无法识别请求来源，请稍后重试");
    }
}
