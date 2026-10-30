package com.itheima.service.setting;

import com.itheima.mapper.AppSettingMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureSwitchServiceTest {

    private static final String KEY = FeatureSwitchService.DOCUMENT_ASSISTANT;

    @Test
    void emptyValueFallsBackToConfigDefault() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(KEY)).thenReturn("");

        var status = new FeatureSwitchService(mapper).status(KEY, true);

        assertTrue(status.enabled());
        assertEquals("config", status.source());
        assertTrue(status.configDefault());
    }

    @Test
    void explicitFalseOverridesConfigDefault() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(KEY)).thenReturn("false");

        var status = new FeatureSwitchService(mapper).status(KEY, true);

        assertFalse(status.enabled(), "数据库显式 false 必须压过配置默认 true");
        assertEquals("database", status.source());
    }

    @Test
    void missingRowFallsBackToConfigDefault() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(KEY)).thenReturn(null);

        var status = new FeatureSwitchService(mapper).status(KEY, false);

        assertFalse(status.enabled());
        assertEquals("config", status.source());
    }

    @Test
    void mapperFailureDegradesToConfigDefaultInsteadOfThrowing() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(anyString())).thenThrow(new RuntimeException("Table 'app_setting' doesn't exist"));

        var status = new FeatureSwitchService(mapper).status(KEY, true);

        assertTrue(status.enabled(), "迁移未执行时应降级为配置默认值，而不是让接口 500");
        assertEquals("config", status.source());
    }

    @Test
    void secondReadWithinTtlDoesNotHitDatabase() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(KEY)).thenReturn("true");
        var service = new FeatureSwitchService(mapper);

        service.status(KEY, false);
        service.status(KEY, false);

        verify(mapper, times(1)).findValue(KEY);
    }

    @Test
    void setWritesAndInvalidatesCacheImmediately() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        when(mapper.findValue(KEY)).thenReturn("true");
        var service = new FeatureSwitchService(mapper);

        assertTrue(service.status(KEY, false).enabled());
        var afterSet = service.set(KEY, false, false, 7L);
        assertFalse(afterSet.enabled());
        assertEquals("database", afterSet.source());
        verify(mapper).upsert(eq(KEY), eq("false"), eq(7L));

        when(mapper.findValue(KEY)).thenReturn("false");
        assertFalse(service.status(KEY, false).enabled(), "写入后缓存必须失效");
    }

    @Test
    void resetClearsOverrideAndFollowsConfigAgain() {
        AppSettingMapper mapper = mock(AppSettingMapper.class);
        var service = new FeatureSwitchService(mapper);

        var status = service.reset(KEY, true, 9L);

        assertTrue(status.enabled());
        assertEquals("config", status.source());
        verify(mapper).upsert(eq(KEY), eq(""), eq(9L));
        verify(mapper, never()).findValue(anyString());
    }
}
