package com.itheima.service.security;

import com.itheima.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable authorization snapshots only; no passwords or profile data. */
@Component
public class AccountAccessCache {
    public static final String REQUEST_ATTRIBUTE=AccountAccessCache.class.getName()+".account";
    private static final long TTL_MS=20_000;
    private static final int MAX_ENTRIES=10_000;
    private final UserMapper mapper;
    private final Clock clock;
    private final ConcurrentHashMap<Long,Cached> entries=new ConcurrentHashMap<>();
    @Autowired
    public AccountAccessCache(UserMapper mapper) { this(mapper,Clock.systemUTC()); }
    public AccountAccessCache(UserMapper mapper,Clock clock) { this.mapper=mapper; this.clock=clock; }
    public Account get(Long userId) {
        if(userId==null) return null;
        long now=clock.millis();
        if(entries.size()>=MAX_ENTRIES) {
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAt<=now);
            if(entries.size()>=MAX_ENTRIES && !entries.containsKey(userId)) {
                var keys=entries.keySet().iterator(); if(keys.hasNext()) entries.remove(keys.next());
            }
        }
        return entries.compute(userId,(id,cached) -> {
            if(cached!=null && cached.expiresAt>now) return cached;
            var user=mapper.findById(id);
            boolean active=user!=null && !Integer.valueOf(1).equals(user.getDeleted())
                    && Integer.valueOf(1).equals(user.getStatus()) && Integer.valueOf(1).equals(user.getLoginEnabled());
            return new Cached(new Account(id,active,active && Integer.valueOf(1).equals(user.getRole())),clock.millis()+TTL_MS);
        }).account;
    }
    public record Account(Long userId,boolean active,boolean admin) {}
    private record Cached(Account account,long expiresAt) {}
}
