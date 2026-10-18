package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.mapper.AiTrialQuotaMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiTrialQuotaServiceTest {
    final AiTrialQuotaMapper mapper=mock(AiTrialQuotaMapper.class);
    final AiTrialProperties properties=new AiTrialProperties();
    final LocalDate date=LocalDate.of(2026,10,19);
    final AiTrialQuotaService service=new AiTrialQuotaService(mapper,properties,
        Clock.fixed(Instant.parse("2026-10-18T23:00:00Z"),ZoneId.of("Asia/Shanghai")));
    @Test void siteAndUserAreLockedInOrderBeforeIncrementing() {
        service.reserve(7L); var order=inOrder(mapper);
        order.verify(mapper).ensureRow(date,"site"); order.verify(mapper).lockCount(date,"site");
        order.verify(mapper).ensureRow(date,"user:7"); order.verify(mapper).lockCount(date,"user:7");
        order.verify(mapper).increment(date,"site"); order.verify(mapper).increment(date,"user:7");
    }
    @Test void userLimitRejectsWithoutIncrementing() {
        when(mapper.lockCount(date,"user:7")).thenReturn(3);
        assertThatThrownBy(() -> service.reserve(7L)).isInstanceOf(BusinessException.class).hasMessageContaining("你今天");
        verify(mapper,never()).increment(any(),anyString());
    }
    @Test void siteLimitRejectsBeforeUserLock() {
        when(mapper.lockCount(date,"site")).thenReturn(100);
        assertThatThrownBy(() -> service.reserve(7L)).hasMessageContaining("全站");
        verify(mapper,never()).ensureRow(date,"user:7"); verify(mapper,never()).increment(any(),anyString());
    }
    @Test void requiresAuthenticatedIdentity() {
        assertThatThrownBy(() -> service.reserve(null)).hasMessageContaining("登录"); verifyNoInteractions(mapper);
    }
    @Test void remainingNeverNegativeAndResetUsesBeijingMidnight() {
        when(mapper.count(date,"user:7")).thenReturn(9); when(mapper.count(date,"site")).thenReturn(104);
        var status=service.status(7L);
        assertThat(status.remaining()).isZero(); assertThat(status.siteRemaining()).isZero();
        assertThat(status.resetsAt()).isEqualTo("2026-10-20T00:00+08:00");
    }
    @Test void newDayStartsWithFullAllowanceWithoutDeletingHistory() {
        assertThat(service.status(7L).remaining()).isEqualTo(3);
        var nextDay=new AiTrialQuotaService(mapper,properties,Clock.fixed(Instant.parse("2026-10-19T16:00:00Z"),ZoneId.of("Asia/Shanghai")));
        assertThat(nextDay.status(7L).resetsAt()).isEqualTo("2026-10-21T00:00+08:00");
        verify(mapper).count(LocalDate.of(2026,10,20),"user:7");
    }
}
