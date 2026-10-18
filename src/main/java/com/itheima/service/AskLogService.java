package com.itheima.service;

import com.itheima.mapper.AskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** Best-effort aggregate logging; never records the system prompt or full model response. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AskLogService {

    private static final Pattern API_KEY = Pattern.compile("(?i)sk-[A-Za-z0-9_-]{8,}");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]{8,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final AskLogMapper askLogMapper;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String model;

    public void record(Long userId, String question, int answerLength, int referenceCount,
                       long elapsedMs, boolean success) {
        recordForModel(userId,question,answerLength,referenceCount,elapsedMs,success,model);
    }

    public void recordForModel(Long userId,String question,int answerLength,int referenceCount,
                               long elapsedMs,boolean success,String usedModel) {
        if (userId == null) {
            return;
        }
        try {
            askLogMapper.insert(userId, redact(question), Math.max(answerLength, 0), Math.max(referenceCount, 0),
                    (int) Math.min(Math.max(elapsedMs, 0), Integer.MAX_VALUE), 0,
                    usedModel == null ? "" : usedModel.substring(0,Math.min(usedModel.length(),64)), success ? 1 : 0);
        } catch (Exception e) {
            // Logging failure must not change the user-visible answer or expose question contents.
            log.warn("ask_log 写入失败，异常类型={}", e.getClass().getSimpleName());
        }
    }

    private static String redact(String question) {
        if (question == null) {
            return "";
        }
        String redacted = API_KEY.matcher(question).replaceAll("[REDACTED_API_KEY]");
        redacted = BEARER_TOKEN.matcher(redacted).replaceAll("Bearer [REDACTED]");
        redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        return redacted.length() <= 200 ? redacted : redacted.substring(0, 200);
    }
}
