package com.itheima.utils;

import com.itheima.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.Locale;

/** Shared, allow-listed search filters for HTTP search and Agent tools. */
public final class SearchFilters {

    private SearchFilters() {
    }

    public static LocalDateTime createdAfter(String timeRange, LocalDateTime now) {
        String value = timeRange == null || timeRange.isBlank() ? "all" : timeRange.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "all" -> null;
            case "1d" -> now.minusDays(1);
            case "7d" -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default -> throw new BusinessException("timeRange 仅支持 all/1d/7d/30d");
        };
    }

    public static String orderBy(String orderBy) {
        String value = orderBy == null || orderBy.isBlank() ? "latest" : orderBy.trim().toLowerCase(Locale.ROOT);
        if ("latest".equals(value) || "popular".equals(value)) {
            return value;
        }
        throw new BusinessException("orderBy 仅支持 latest 或 popular");
    }
}
