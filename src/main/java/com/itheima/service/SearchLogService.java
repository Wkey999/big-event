package com.itheima.service;

import com.itheima.mapper.SearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** Best-effort search telemetry; never stores article bodies or credentials pasted into a query. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

    private static final Pattern API_KEY = Pattern.compile("(?i)sk-[A-Za-z0-9_-]{8,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final SearchLogMapper searchLogMapper;

    public void record(Long userId, String keyword, Long categoryId, String timeRange, long resultCount, long elapsedMs) {
        if (userId == null) {
            return;
        }
        try {
            searchLogMapper.insert(userId, redact(keyword), categoryId, timeRange,
                    (int) Math.min(Math.max(resultCount, 0), Integer.MAX_VALUE),
                    (int) Math.min(Math.max(elapsedMs, 0), Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("search_log 写入失败，异常类型={}", e.getClass().getSimpleName());
        }
    }

    private static String redact(String keyword) {
        if (keyword == null) {
            return "";
        }
        String redacted = API_KEY.matcher(keyword).replaceAll("[REDACTED_API_KEY]");
        redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        return redacted.length() <= 100 ? redacted : redacted.substring(0, 100);
    }
}
