package com.itheima.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {
    @Test
    void repeatedLoginsIssueDistinctTokensSoLogoutTargetsOneSession() {
        var jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secret", "test-only-secret-with-at-least-32-bytes");
        ReflectionTestUtils.setField(jwt, "expiration", 86_400_000L);

        String first = jwt.generateToken(7L, "reader");
        String second = jwt.generateToken(7L, "reader");

        assertThat(first).isNotEqualTo(second);
        assertThat(jwt.getUserId(first)).isEqualTo(7L);
        assertThat(jwt.getUserId(second)).isEqualTo(7L);
    }
}
