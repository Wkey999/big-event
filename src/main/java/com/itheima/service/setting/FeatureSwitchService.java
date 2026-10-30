package com.itheima.service.setting;

import com.itheima.mapper.AppSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时功能开关。
 *
 * <p>语义：数据库里为空 → 跟随配置文件默认值；显式 true/false → 覆盖配置。
 * <p>缓存 5 秒，管理员改完最多 5 秒生效，无需重启；写操作立即失效缓存。
 * <p>数据库异常（例如迁移未执行）时**降级为跟随配置**，不让整个功能 500。
 */
@Slf4j
@Service
public class FeatureSwitchService {

    public static final String DOCUMENT_ASSISTANT = "document-assistant.enabled";

    private static final long CACHE_TTL_MS = 5_000L;

    private final AppSettingMapper mapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public FeatureSwitchService(AppSettingMapper mapper) {
        this.mapper = mapper;
    }

    /** 文档助手当前是否可用（数据库开关优先，缺省跟随配置）。 */
    public boolean documentAssistantEnabled(boolean configDefault) {
        return status(DOCUMENT_ASSISTANT, configDefault).enabled();
    }

    /** 供接口返回：当前值、来源（database/config）、配置默认值。 */
    public SwitchStatus status(String key, boolean configDefault) {
        String raw = rawValue(key);
        boolean overridden = raw != null && !raw.isBlank();
        boolean enabled = overridden ? Boolean.parseBoolean(raw.trim()) : configDefault;
        return new SwitchStatus(enabled, overridden ? "database" : "config", configDefault);
    }

    /** 管理员写入开关；返回写入后的状态。 */
    public SwitchStatus set(String key, boolean enabled, boolean configDefault, Long adminId) {
        mapper.upsert(key, Boolean.toString(enabled), adminId);
        cache.remove(key);
        return new SwitchStatus(enabled, "database", configDefault);
    }

    /** 清空为“跟随配置”。 */
    public SwitchStatus reset(String key, boolean configDefault, Long adminId) {
        mapper.upsert(key, "", adminId);
        cache.remove(key);
        return new SwitchStatus(configDefault, "config", configDefault);
    }

    private String rawValue(String key) {
        Cached cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at() < CACHE_TTL_MS) {
            return cached.raw();
        }
        try {
            String raw = mapper.findValue(key);
            cache.put(key, new Cached(raw, now));
            return raw;
        } catch (RuntimeException e) {
            // 迁移未执行/数据库抖动时降级，避免把功能开关变成 500
            log.warn("读取功能开关失败，降级为配置默认值: key={}, err={}", key, e.getClass().getSimpleName());
            return null;
        }
    }

    private record Cached(String raw, long at) {}

    public record SwitchStatus(boolean enabled, String source, boolean configDefault) {}
}
