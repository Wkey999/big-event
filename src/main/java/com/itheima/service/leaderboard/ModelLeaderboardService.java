package com.itheima.service.leaderboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads the CC BY 4.0 Arena leaderboard snapshots; never invents missing scores. */
@Service
public class ModelLeaderboardService {
    private static final Logger log = LoggerFactory.getLogger(ModelLeaderboardService.class);
    private static final String DATASET_URL = "https://huggingface.co/datasets/lmarena-ai/leaderboard-dataset";
    private static final String ROWS_URL = "https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config={config}&split=latest&offset=0&length=100";
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(2);
    private static final Duration FAILURE_RETRY = Duration.ofMinutes(5);

    private final ObjectMapper mapper;
    private final RestClient client;
    private final Clock clock;
    private final Map<Dimension, CachedBoard> cache = new ConcurrentHashMap<>();

    @Autowired
    public ModelLeaderboardService(ObjectMapper mapper,
                                   @Value("${leaderboard.proxy-url:}") String configuredProxyUrl) {
        this(mapper, defaultClient(configuredProxyUrl), Clock.systemUTC());
    }

    ModelLeaderboardService(ObjectMapper mapper, RestClient client, Clock clock) {
        this.mapper = mapper;
        this.client = client;
        this.clock = clock;
    }

    private static RestClient defaultClient(String configuredProxyUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        String proxyUrl = configuredProxyUrl.isBlank() ? System.getenv("HTTPS_PROXY") : configuredProxyUrl;
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI uri = URI.create(proxyUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1) {
                throw new IllegalArgumentException("leaderboard.proxy-url must be an HTTP proxy URL with a port");
            }
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(uri.getHost(), uri.getPort())));
        }
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public synchronized Board get(String requestedDimension) {
        Dimension dimension = Dimension.from(requestedDimension);
        Instant now = clock.instant();
        CachedBoard cached = cache.get(dimension);
        if (cached != null && now.isBefore(cached.retryAfter)) return cached.board;
        try {
            String body = client.get().uri(URI.create(ROWS_URL.replace("{config}", dimension.config)))
                    .retrieve().body(String.class);
            if (body == null || body.length() > 1_000_000) {
                throw new IllegalStateException("Unexpected leaderboard response size");
            }
            Board board = parse(dimension, mapper.readTree(body), now);
            if (board.items().isEmpty()) throw new IllegalStateException("No valid overall leaderboard rows");
            cache.put(dimension, new CachedBoard(board, now.plus(REFRESH_INTERVAL)));
            return board;
        } catch (Exception error) {
            // Keep a last-good snapshot; do not expose upstream errors or hammer the public API.
            log.warn("榜单同步失败 dimension={} type={}", dimension.key, error.getClass().getSimpleName());
            Board board = cached == null || cached.board.items().isEmpty()
                    ? new Board(dimension.key, dimension.label, dimension.metric, DATASET_URL, null,
                    null, "unavailable", List.of())
                    : new Board(dimension.key, dimension.label, dimension.metric, DATASET_URL,
                    cached.board.sourceDate(), cached.board.checkedAt(), "stale", cached.board.items());
            cache.put(dimension, new CachedBoard(board, now.plus(FAILURE_RETRY)));
            return board;
        }
    }

    static Board parse(Dimension dimension, JsonNode root, Instant checkedAt) {
        JsonNode rows = root.path("rows");
        if (!rows.isArray()) throw new IllegalArgumentException("Missing leaderboard rows");
        List<Model> models = new ArrayList<>();
        String sourceDate = null;
        for (JsonNode wrapper : rows) {
            JsonNode row = wrapper.path("row");
            if (!"overall".equalsIgnoreCase(row.path("category").asText(""))) continue;
            int rank = row.path("rank").asInt(0);
            JsonNode scoreNode = row.path(dimension.scoreField);
            if (rank < 1 || rank > 100 || !scoreNode.isNumber()) continue;
            double score = scoreNode.asDouble();
            if (!Double.isFinite(score)) continue;
            String name = row.path("model_name").asText("").trim();
            if (name.isEmpty() || name.length() > 160) continue;
            String published = row.path("leaderboard_publish_date").asText("");
            if (!published.matches("\\d{4}-\\d{2}-\\d{2}")) published = null;
            if (sourceDate == null) sourceDate = published;
            models.add(new Model(rank, name, row.path("organization").asText(""),
                    row.path("license").asText(""), score, published));
        }
        models.sort(Comparator.comparingInt(Model::rank));
        return new Board(dimension.key, dimension.label, dimension.metric, DATASET_URL,
                sourceDate, checkedAt.toString(), "available", List.copyOf(models));
    }

    public record Model(int rank, String name, String organization, String license,
                        double score, String publishedDate) {}

    public record Board(String dimension, String label, String metric, String sourceUrl,
                        String sourceDate, String checkedAt, String status, List<Model> items) {}

    private record CachedBoard(Board board, Instant retryAfter) {}

    enum Dimension {
        TEXT("text", "对话", "Arena Score", "text_style_control", "rating"),
        WEBDEV("webdev", "编程建站", "Arena Score", "webdev", "rating"),
        SEARCH("search", "搜索", "Arena Score", "search_style_control", "rating"),
        VISION("vision", "视觉理解", "Arena Score", "vision_style_control", "rating"),
        AGENT("agent", "Agent", "IPS 分数", "agent", "score");

        final String key;
        final String label;
        final String metric;
        final String config;
        final String scoreField;

        Dimension(String key, String label, String metric, String config, String scoreField) {
            this.key = key;
            this.label = label;
            this.metric = metric;
            this.config = config;
            this.scoreField = scoreField;
        }

        static Dimension from(String key) {
            String normalized = key == null ? "text" : key.trim().toLowerCase(Locale.ROOT);
            for (Dimension item : values()) if (item.key.equals(normalized)) return item;
            throw new com.itheima.exception.BusinessException("不支持的榜单维度");
        }
    }
}
