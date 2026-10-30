package com.itheima.controller;

import com.itheima.exception.BusinessException;
import com.itheima.service.setting.FeatureSwitchService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运行时开关对文档助手网关的影响：配置默认 false，但管理员打开数据库开关后必须可用。
 * 这里不触发真实 Python 调用（未启用时应在调用前就抛出业务错误）。
 */
class DocumentAssistantSwitchTest {

    @Test
    void disabledByDefaultReportsConfigSource() {
        var controller = new DocumentAssistantController(false, "http://127.0.0.1:8001");

        var status = controller.status().getData();

        assertFalse(status.enabled());
        assertEquals("config", status.source());
        assertThrows(BusinessException.class,
                () -> controller.analyze(new DocumentAssistantController.AnalyzeRequest("正文", "问题")));
    }

    @Test
    void adminSwitchTurnsGatewayOnWithoutRestart() {
        FeatureSwitchService switches = mock(FeatureSwitchService.class);
        when(switches.documentAssistantEnabled(false)).thenReturn(true);
        when(switches.status(FeatureSwitchService.DOCUMENT_ASSISTANT, false))
                .thenReturn(new FeatureSwitchService.SwitchStatus(true, "database", false));

        var controller = new DocumentAssistantController(false, "http://127.0.0.1:8001", null, null, null, switches);

        assertTrue(controller.status().getData().enabled());
        assertEquals("database", controller.status().getData().source());
        // 用空正文触发本地校验：证明请求已越过“尚未启用”这道门，且不会真的访问 Python 侧车
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.analyze(new DocumentAssistantController.AnalyzeRequest("   ", "问题")));
        assertTrue(ex.getMessage().contains("请输入文档内容"), "应被入参校验拦下：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("尚未启用"));
    }

    @Test
    void adminSwitchOffBlocksEvenWhenConfigDefaultIsTrue() {
        FeatureSwitchService switches = mock(FeatureSwitchService.class);
        when(switches.documentAssistantEnabled(true)).thenReturn(false);

        var controller = new DocumentAssistantController(true, "http://127.0.0.1:8001", null, null, null, switches);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.analyze(new DocumentAssistantController.AnalyzeRequest("正文", "问题")));
        assertTrue(ex.getMessage().contains("尚未启用"));
    }

    @Test
    void adminSwitchToleratesMissingService() {
        FeatureSwitchService switches = mock(FeatureSwitchService.class);
        when(switches.documentAssistantEnabled(false)).thenReturn(true);
        when(switches.status(FeatureSwitchService.DOCUMENT_ASSISTANT, false))
                .thenReturn(new FeatureSwitchService.SwitchStatus(true, "database", false));

        var controller = new DocumentAssistantController(false, "http://127.0.0.1:8001", null, null, null, switches);

        assertEquals(200, controller.status().getCode() == 0 ? 200 : 0);
        assertTrue(controller.status().getData().enabled());
        // 计数器 mock 未被调用时也不应抛错
        org.mockito.Mockito.verify(switches, org.mockito.Mockito.times(2))
                .status(FeatureSwitchService.DOCUMENT_ASSISTANT, false);
    }
}
