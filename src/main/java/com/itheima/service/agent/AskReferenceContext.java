package com.itheima.service.agent;

import com.itheima.pojo.ArticleReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-request reference collector. Tool calls are synchronous and the context is cleared in finally. */
public final class AskReferenceContext {

    private static final int MAX_REFERENCES = 20;
    private static final ThreadLocal<Map<Long, ArticleReference>> REFERENCES = new ThreadLocal<>();

    private AskReferenceContext() {
    }

    public static void begin() {
        REFERENCES.set(new LinkedHashMap<>());
    }

    public static void addAll(List<ArticleReference> references) {
        Map<Long, ArticleReference> current = REFERENCES.get();
        if (current == null || references == null) {
            return;
        }
        for (ArticleReference reference : references) {
            if (current.size() >= MAX_REFERENCES) {
                break;
            }
            current.putIfAbsent(reference.id(), reference);
        }
    }

    public static List<ArticleReference> snapshot() {
        Map<Long, ArticleReference> current = REFERENCES.get();
        return current == null ? List.of() : new ArrayList<>(current.values());
    }

    public static void clear() {
        REFERENCES.remove();
    }
}
