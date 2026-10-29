package com.itheima.service.security;

import com.itheima.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/** Stores only a token digest, and only until the JWT itself expires. */
@Service
public class TokenRevocationService {
    private static final String KEY_PREFIX = "boom:auth:revoked:v1:";

    private final StringRedisTemplate redis;
    private final JwtUtils jwtUtils;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public TokenRevocationService(StringRedisTemplate redis, JwtUtils jwtUtils,
                                  @Value("${app.auth.token-revocation.enabled:false}") boolean enabled) {
        this(redis, jwtUtils, enabled, Clock.systemUTC());
    }

    TokenRevocationService(StringRedisTemplate redis, JwtUtils jwtUtils, boolean enabled, Clock clock) {
        this.redis = redis;
        this.jwtUtils = jwtUtils;
        this.enabled = enabled;
        this.clock = clock;
    }

    public boolean isRevoked(String token) {
        if (!enabled) return false;
        Boolean present = redis.hasKey(key(token));
        if (present == null) throw new IllegalStateException("Redis revocation lookup returned no result");
        return present;
    }

    public void revoke(String token) {
        if (!enabled) return;
        Claims claims = jwtUtils.parseToken(token);
        Duration remaining = Duration.between(clock.instant(), claims.getExpiration().toInstant());
        if (remaining.isPositive()) {
            redis.opsForValue().set(key(token), "1", remaining);
        }
    }

    private static String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
