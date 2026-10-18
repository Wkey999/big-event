package com.itheima.service.agent;

import com.itheima.config.AiTrialProperties;
import com.itheima.exception.BusinessException;
import com.itheima.pojo.UserAiConfig;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSearchClientRouterTest {
    final UserAiClientFactory factory=mock(UserAiClientFactory.class);
    final AiTrialQuotaService quota=mock(AiTrialQuotaService.class);
    final AiTrialProperties properties=new AiTrialProperties();
    final AiSearchClientRouter router=new AiSearchClientRouter(factory,quota,properties);
    @AfterEach void cleanup() { ThreadLocalUtils.clear(); }
    @Test void trialIsDisabledWithoutDedicatedKeyAndCannotFallBack() {
        properties.setEnabled(true);
        assertThatThrownBy(() -> router.create("platform",null)).isInstanceOf(BusinessException.class).hasMessageContaining("暂未开放");
        verifyNoInteractions(factory,quota);
    }
    @Test void customModeNeverUsesTrialOrConsumesTrialQuota() {
        var config=UserAiClientFactoryTest.config("openai","fake-personal-key");
        var client=mock(ChatClient.class); when(factory.create(config)).thenReturn(client);
        assertThat(router.create("custom",config)).isSameAs(client);
        verifyNoInteractions(quota);
    }
    @Test void missingCustomKeyIsNotReplacedByTrialKey() {
        properties.setEnabled(true); properties.setApiKey("fake-trial-key");
        when(factory.create(null)).thenThrow(new BusinessException("请先配置你自己的 API Key"));
        assertThatThrownBy(() -> router.create("custom",null)).hasMessageContaining("自己的 API Key");
        verifyNoInteractions(quota);
    }
    @Test void platformUsesFixedTrialConfigurationAndReservesBeforeReturningClient() {
        properties.setEnabled(true); properties.setApiKey("fake-trial-key"); ThreadLocalUtils.setUserId(7L);
        var client=mock(ChatClient.class); when(factory.create(any())).thenReturn(client);
        assertThat(router.create("platform",null)).isSameAs(client);
        var config=ArgumentCaptor.forClass(UserAiConfig.class); verify(factory).create(config.capture());
        assertThat(config.getValue().getProvider()).isEqualTo("deepseek");
        assertThat(config.getValue().getModel()).isEqualTo("deepseek-flash");
        assertThat(config.getValue().getApiKey()).isEqualTo("fake-trial-key"); verify(quota).reserve(7L);
    }
    @Test void exhaustedQuotaDoesNotReturnUsableTrialClient() {
        properties.setEnabled(true); properties.setApiKey("fake-trial-key"); ThreadLocalUtils.setUserId(7L);
        doThrow(new BusinessException("次数已用完")).when(quota).reserve(7L);
        assertThatThrownBy(() -> router.create("platform",null)).hasMessage("次数已用完");
    }
    @Test void mixedModeIsRejectedRatherThanSilentlyChargingAnotherAccount() {
        assertThatThrownBy(() -> router.create("platform",UserAiClientFactoryTest.config("deepseek","fake-personal-key")))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> router.create("unknown",null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(factory,quota);
    }
}
