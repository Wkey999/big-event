package com.itheima.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The model client is only exposed after a developer explicitly enables the optional LLM profile.
 * This keeps ordinary database-backed development and tests independent of provider credentials.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
public class LlmChatConfiguration {

    @Bean
    ChatClient llmChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
