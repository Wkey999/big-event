package com.itheima.service.security;

import com.itheima.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.*;

class ClientAddressResolverTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }
    @Test void forwardingHeadersCannotReplaceSocketPeer() {
        var request=new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.addHeader("X-Forwarded-For","198.51.100.1");
        request.addHeader("Forwarded","for=198.51.100.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(new ClientAddressResolver().current()).isEqualTo("203.0.113.8");
    }
    @Test void missingRequestIsRejected() {
        assertThatThrownBy(() -> new ClientAddressResolver().current()).isInstanceOf(BusinessException.class);
    }
}
