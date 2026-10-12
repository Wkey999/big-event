package com.itheima.service;

import com.itheima.mapper.AskLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AskLogServiceTest {

    @Mock
    private AskLogMapper askLogMapper;

    @Test
    void redactsCredentialsAndPersonalEmailBeforePersistingQuestion() {
        AskLogService service = new AskLogService(askLogMapper);

        service.record(7L, "key sk-1234567890abc and mail person@example.com", 21, 2, 77, true);

        verify(askLogMapper).insert(7L, "key [REDACTED_API_KEY] and mail [REDACTED_EMAIL]", 21, 2, 77, 0, "", 1);
    }
}
