package com.itheima.service.security;

import com.itheima.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenRevocationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JwtUtils jwt = mock(JwtUtils.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void logoutStoresDigestOnlyUntilJwtExpiryAndLookupFindsIt() {
        String token = "signed.jwt.payload";
        Claims claims = mock(Claims.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(jwt.parseToken(token)).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(Date.from(NOW.plusSeconds(90)));
        when(redis.opsForValue()).thenReturn(values);

        TokenRevocationService service = new TokenRevocationService(redis, jwt, true, clock);
        service.revoke(token);

        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(90)));
        assertThat(key.getValue()).matches("boom:auth:revoked:v1:[0-9a-f]{64}");
        assertThat(key.getValue()).doesNotContain(token);
        when(redis.hasKey(key.getValue())).thenReturn(true);
        assertThat(service.isRevoked(token)).isTrue();
    }

    @Test
    void disabledModeNeverTouchesRedis() {
        TokenRevocationService service = new TokenRevocationService(redis, jwt, false, clock);
        service.revoke("token");
        assertThat(service.isRevoked("token")).isFalse();
        verifyNoInteractions(redis, jwt);
    }

    @Test
    void expiredJwtNeverCreatesRevocationEntry() {
        Claims claims = mock(Claims.class);
        when(jwt.parseToken("token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(Date.from(NOW.minusSeconds(1)));
        new TokenRevocationService(redis, jwt, true, clock).revoke("token");
        verify(redis, never()).opsForValue();
    }

    @Test
    void missingRedisLookupResultDoesNotSilentlyAcceptToken() {
        var service = new TokenRevocationService(redis, jwt, true, clock);
        when(redis.hasKey(any(String.class))).thenReturn(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.isRevoked("token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
