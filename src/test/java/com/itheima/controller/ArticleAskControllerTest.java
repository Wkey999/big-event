package com.itheima.controller;

import com.itheima.exception.BusinessException;
import com.itheima.pojo.AskDTO;
import com.itheima.service.AskLogService;
import com.itheima.service.agent.AgentAskService;
import com.itheima.utils.ThreadLocalUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleAskControllerTest {

    @Test
    void returnsReadableBusinessErrorWhenLlmIsNotEnabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentAskService> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AskLogService> askLogProvider = mock(ObjectProvider.class);
        AskLogService askLogService = mock(AskLogService.class);
        when(provider.getIfAvailable()).thenReturn(null);
        when(askLogProvider.getIfAvailable()).thenReturn(askLogService);
        ThreadLocalUtils.setUserId(7L);
        ArticleAskController controller = new ArticleAskController(provider, askLogProvider, mock(ObjectProvider.class));
        AskDTO request = new AskDTO();
        request.setQuestion("find a Spring article");

        assertThatThrownBy(() -> controller.ask(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("智能问答未启用");
        verify(askLogService).record(7L, "find a Spring article", 0, 0, 0, false);
        ThreadLocalUtils.clear();
    }
}
