package com.itheima.controller;

import com.itheima.pojo.Result;
import com.itheima.service.setting.FeatureSwitchService;
import com.itheima.utils.ThreadLocalUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员功能开关（/admin/** 由 AdminInterceptor 统一鉴权）。
 * 文档助手的开关决定：后端是否可用 + 前端入口是否显示。
 */
@RestController
@RequestMapping("/admin/settings")
public class AdminSettingController {

    private final FeatureSwitchService switches;
    private final boolean documentAssistantConfigDefault;

    public AdminSettingController(FeatureSwitchService switches,
                                  @org.springframework.beans.factory.annotation.Value("${app.document-assistant.enabled:false}")
                                  boolean documentAssistantConfigDefault) {
        this.switches = switches;
        this.documentAssistantConfigDefault = documentAssistantConfigDefault;
    }

    @GetMapping("/document-assistant")
    public Result<FeatureSwitchService.SwitchStatus> getDocumentAssistant() {
        return Result.success(switches.status(FeatureSwitchService.DOCUMENT_ASSISTANT, documentAssistantConfigDefault));
    }

    /** enabled=true/false 显式覆盖；不传 body、传 {} 或传 null 都表示恢复“跟随配置”。 */
    @PutMapping("/document-assistant")
    public Result<FeatureSwitchService.SwitchStatus> updateDocumentAssistant(
            @RequestBody(required = false) ToggleRequest request) {
        Long adminId = ThreadLocalUtils.getUserId();
        if (request == null || request.enabled() == null) {
            return Result.success(switches.reset(FeatureSwitchService.DOCUMENT_ASSISTANT, documentAssistantConfigDefault, adminId));
        }
        return Result.success(switches.set(FeatureSwitchService.DOCUMENT_ASSISTANT, request.enabled(),
                documentAssistantConfigDefault, adminId));
    }

    public record ToggleRequest(Boolean enabled) {}
}
