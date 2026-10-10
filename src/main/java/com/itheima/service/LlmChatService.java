package com.itheima.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.llm", name = "enabled", havingValue = "true")
public class LlmChatService {

    private final ChatClient llmChatClient;

    public String echo(String message) {
        return llmChatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
