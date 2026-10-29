package com.itheima.service.leaderboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.mapper.LeaderboardSnapshotMapper.StateRow;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/** Fetches Arena data only during scheduled sync; requests read committed MySQL snapshots. */
@Service
public class ModelLeaderboardService {
    private static final Logger log = LoggerFactory.getLogger(ModelLeaderboardService.class);
    static final String DATASET_URL = "https://huggingface.co/datasets/lmarena-ai/leaderboard-dataset";
    private static final String ROWS_URL = "https://datasets-server.huggingface.co/rows?dataset=lmarena-ai%2Fleaderboard-dataset&config={config}&split=latest&offset=0&length=100";
    private static final Duration FAILURE_RETRY = Duration.ofMinutes(5);
    private static final int MAX_DAILY_ATTEMPTS = 3;

    private final ObjectMapper mapper;
    private final RestClient client;
    private final Clock clock;
    private final LeaderboardSnapshotStore store;
    private final ZoneId syncZone;
    private final Set<Dimension> syncing = ConcurrentHashMap.newKeySet();

    @Autowired
    public ModelLeaderboardService(ObjectMapper mapper,
                                   LeaderboardSnapshotStore store,
                                   @Value("${leaderboard.proxy-url:}") String configuredProxyUrl,
                                   @Value("${app.leaderboard.time-zone:Asia/Shanghai}") String timeZone,
                                   @Value("${app.leaderboard.connect-timeout:5s}") Duration connectTimeout,
                                   @Value("${app.leaderboard.read-timeout:12s}") Duration readTimeout) {
        this(mapper, defaultClient(configuredProxyUrl, connectTimeout, readTimeout),
                Clock.systemUTC(), store, ZoneId.of(timeZone));
    }

    ModelLeaderboardService(ObjectMapper mapper, RestClient client, Clock clock,
                            LeaderboardSnapshotStore store, ZoneId syncZone) {
        this.mapper = mapper;
        this.client = client;
        this.clock = clock;
        this.store = store;
        this.syncZone = syncZone;
    }

    private static RestClient defaultClient(String configuredProxyUrl, Duration connectTimeout, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
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

    public Board get(String requestedDimension) {
        return store.current(Dimension.from(requestedDimension));
    }

    public void syncAll() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, syncZone);
        for (Dimension dimension : Dimension.values()) {
            try {
                if (due(store.state(dimension), today, now)) sync(dimension);
            } catch (Exception error) {
                log.warn("榜单同步调度失败 dimension={} type={}", dimension.key, error.getClass().getSimpleName());
            }
        }
    }

    /** One bootstrap attempt after deployment, then at most two five-minute retries on a failed day. */
    public void retryFailedToday() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, syncZone);
        for (Dimension dimension : Dimension.values()) {
            try {
                StateRow state = store.state(dimension);
                if (state == null || (today.equals(state.getLastAttemptDay()) && due(state, today, now))) {
                    sync(dimension);
                }
            } catch (Exception error) {
                log.warn("榜单重试调度失败 dimension={} type={}", dimension.key, error.getClass().getSimpleName());
            }
        }
    }

    private static boolean due(StateRow state, LocalDate today, Instant now) {
        if (state == null || !today.equals(state.getLastAttemptDay())) return true;
        return Integer.valueOf(2).equals(state.getLastStatus())
                && state.getAttemptCount() != null && state.getAttemptCount() < MAX_DAILY_ATTEMPTS
                && state.getLastAttemptAt() != null
                && !state.getLastAttemptAt().toInstant(ZoneOffset.UTC).isAfter(now.minus(FAILURE_RETRY));
    }

    void sync(Dimension dimension) {
        if (!syncing.add(dimension)) return;
        Instant now = clock.instant();
        String stage = "upstream";
        try {
            String body = client.get().uri(URI.create(ROWS_URL.replace("{config}", dimension.config)))
                    .retrieve().body(String.class);
            if (body == null || body.length() > 1_000_000) {
                throw new IllegalStateException("Unexpected leaderboard response size");
            }
            Board board = parse(dimension, mapper.readTree(body), now);
            if (board.items().isEmpty()) throw new IllegalStateException("No valid overall leaderboard rows");
            String contentHash = hash(dimension, board);
            stage = "database";
            Instant completedAt = clock.instant();
            store.saveSuccess(dimension, board, contentHash, completedAt,
                    LocalDate.ofInstant(completedAt, syncZone));
        } catch (Exception error) {
            String summary = "database".equals(stage)
                    ? "快照写入失败：" + error.getClass().getSimpleName() : failureSummary(error);
            log.warn("榜单同步失败 dimension={} summary={}", dimension.key, summary);
            try {
                Instant failedAt = clock.instant();
                store.saveFailure(dimension, failedAt, LocalDate.ofInstant(failedAt, syncZone), summary);
            }
            catch (Exception persistenceError) {
                log.error("榜单同步状态写入失败 dimension={} type={}", dimension.key,
                        persistenceError.getClass().getSimpleName());
            }
        } finally {
            syncing.remove(dimension);
        }
    }

    private String hash(Dimension dimension, Board board) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(dimension.key.getBytes(StandardCharsets.US_ASCII));
        digest.update(mapper.writeValueAsBytes(List.of(board.sourceDate() == null ? "" : board.sourceDate(),
                board.metric(), board.items())));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String failureSummary(Exception error) {
        if (error instanceof RestClientResponseException response)
            return "上游 HTTP " + response.getStatusCode().value();
        if (error instanceof ResourceAccessException) return "上游连接或读取超时/失败";
        if (error instanceof IllegalStateException) return "上游响应缺少有效榜单数据";
        return "同步失败：" + error.getClass().getSimpleName();
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
            try { LocalDate.parse(published); }
            catch (DateTimeParseException ignored) { published = null; }
            String organization = row.path("organization").asText("");
            String license = row.path("license").asText("");
            if (organization.length() > 255 || license.length() > 255) continue;
            if (published != null && (sourceDate == null || published.compareTo(sourceDate) > 0)) {
                sourceDate = published;
            }
            models.add(new Model(rank, name, organization, license, score, published,
                    false, null, null, null));
        }
        models.sort(Comparator.comparingInt(Model::rank).thenComparing(Model::name));
        return new Board(dimension.key, dimension.label, dimension.metric, DATASET_URL,
                sourceDate, checkedAt.toString(), "available", List.copyOf(models),
                null, null, null, null, "never", "尚未保存");
    }

    public record Model(int rank, String name, String organization, String license,
                        double score, String publishedDate, boolean newlyListed,
                        Integer previousRank, Integer rankChange, String rankTrend) {}

    public record Board(String dimension, String label, String metric, String sourceUrl,
                        String sourceDate, String checkedAt, String status, List<Model> items,
                        Long snapshotId, String snapshotSyncedAt, String lastSuccessfulSyncAt,
                        String lastAttemptAt, String lastAttemptStatus, String syncSummary) {}

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
