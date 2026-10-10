package com.itheima.config;

import com.itheima.service.LlmChatService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LlmChatConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenAiChatAutoConfiguration.class,
                    ChatClientAutoConfiguration.class))
            .withUserConfiguration(LlmChatConfiguration.class, LlmChatService.class);

    @Test
    void leavesLlmBeansDisabledWhenLocalConfigurationIsAbsent() {
        contextRunner.withPropertyValues("spring.ai.model.chat=none", "app.llm.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LlmChatService.class);
            assertThat(context).doesNotHaveBean(ChatClient.class);
            assertThat(context).doesNotHaveBean(ChatModel.class);
        });
    }
}
