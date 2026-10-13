package com.itheima.utils;

/** Escapes user-supplied SQL LIKE metacharacters while still using prepared statements. */
public final class LikeUtils {

    private LikeUtils() {
    }

    public static String escape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
