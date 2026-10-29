import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One-off, local-only importer for reviewed BOOM learning articles. No existing rows are changed. */
public class ImportLearningArticles {
    private static final String AUTHOR = "boom_learning_editor";
    private static final String CATEGORY = "AI/技术热点";
    private static final Path DATA = Path.of("tools/data/learning-articles.html");
    private static final Pattern HEADER = Pattern.compile("(?m)^<!-- ARTICLE:(.*?)\\|\\|\\|(.*?)\\|\\|\\|(https://\\S+) -->\\r?$");

    record Note(String title, String summary, String source, String html) {}

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && "--apply".equals(args[0]);
        if (args.length > 1 || (args.length == 1 && !apply && !"--check".equals(args[0]))) {
            throw new IllegalArgumentException("Usage: [--check|--apply]");
        }
        List<Note> notes = load();
        System.out.printf("Validated %d learning articles; title range: %s / %s%n",
                notes.size(), notes.getFirst().title(), notes.getLast().title());
        if (!apply) return;

        String password = System.getenv("BOOM_SEED_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Set BOOM_SEED_DB_PASSWORD in this process before --apply");
        }
        String url = System.getenv().getOrDefault("BOOM_SEED_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/big_event?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        String username = System.getenv().getOrDefault("BOOM_SEED_DB_USER", "root");
        // Guard against accidentally seeding a remote host or a different database.
        if (!url.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/big_event\\?.*$")) {
            throw new IllegalArgumentException("Importer accepts only localhost/big_event");
        }
        try (Connection db = DriverManager.getConnection(url, username, password)) {
            db.setAutoCommit(false);
            try {
                long categoryId = activeCategoryId(db);
                long authorId = authorId(db);
                int inserted = 0;
                int skipped = 0;
                try (PreparedStatement exists = db.prepareStatement("SELECT id FROM article WHERE title=? LIMIT 1");
                     PreparedStatement insert = db.prepareStatement("INSERT INTO article "
                             + "(title,content,cover_img,summary,user_id,category_id,state,view_count,like_count,collect_count,comment_count,deleted) "
                             + "VALUES (?,?,'',?,?,?,1,0,0,0,0,0)")) {
                    for (Note note : notes) {
                        exists.setString(1, note.title());
                        try (ResultSet rs = exists.executeQuery()) {
                            if (rs.next()) { skipped++; continue; }
                        }
                        insert.setString(1, note.title());
                        insert.setString(2, note.html());
                        insert.setString(3, note.summary());
                        insert.setLong(4, authorId);
                        insert.setLong(5, categoryId);
                        inserted += insert.executeUpdate();
                    }
                }
                db.commit();
                System.out.printf("Committed: inserted=%d, existing_titles_skipped=%d, author=%s%n",
                        inserted, skipped, AUTHOR);
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
    }

    private static List<Note> load() throws Exception {
        String data = Files.readString(DATA, StandardCharsets.UTF_8);
        Matcher matcher = HEADER.matcher(data);
        List<int[]> bounds = new ArrayList<>();
        List<String[]> headers = new ArrayList<>();
        while (matcher.find()) {
            bounds.add(new int[]{matcher.start(), matcher.end()});
            headers.add(new String[]{matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim()});
        }
        if (headers.size() != 12) throw new IllegalArgumentException("Expected exactly 12, found " + headers.size());
        List<Note> notes = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String[] header = headers.get(i);
            String body = data.substring(bounds.get(i)[1], i + 1 < bounds.size() ? bounds.get(i + 1)[0] : data.length()).trim();
            Note note = new Note(header[0], header[1], header[2], body);
            String plain = body.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ");
            if (note.title().isBlank() || note.title().length() > 100 || note.summary().isBlank()
                    || note.summary().length() > 500 || !note.source().startsWith("https://")
                    || plain.length() < 350 || !body.contains(note.source())
                    || body.toLowerCase().contains("<script") || body.toLowerCase().contains("<iframe")
                    || !titles.add(note.title())) {
                throw new IllegalArgumentException("Invalid learning article " + (i + 1) + ": " + note.title());
            }
            notes.add(note);
        }
        return notes;
    }

    private static long activeCategoryId(Connection db) throws Exception {
        try (PreparedStatement q = db.prepareStatement(
                "SELECT id FROM category WHERE category_name=? AND status=1 AND deleted=0 LIMIT 1")) {
            q.setString(1, CATEGORY);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Active category missing: " + CATEGORY);
                return rs.getLong(1);
            }
        }
    }

    private static long authorId(Connection db) throws Exception {
        try (PreparedStatement q = db.prepareStatement(
                "SELECT id,user_type,login_enabled,deleted FROM user WHERE username=? LIMIT 1")) {
            q.setString(1, AUTHOR);
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt(2) != 1 || rs.getInt(3) != 0 || rs.getInt(4) != 0)
                        throw new IllegalStateException("Reserved username is not a non-login machine author");
                    return rs.getLong(1);
                }
            }
        }
        String disabledPassword = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(12));
        try (PreparedStatement insert = db.prepareStatement(
                "INSERT INTO user(username,password,nickname,role,user_type,status,login_enabled,deleted) "
                        + "VALUES (?,?,?,0,1,1,0,0)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, AUTHOR);
            insert.setString(2, disabledPassword);
            insert.setString(3, "BOOM 学习文章");
            insert.executeUpdate();
            try (ResultSet rs = insert.getGeneratedKeys()) {
                if (!rs.next()) throw new IllegalStateException("Author creation returned no key");
                return rs.getLong(1);
            }
        }
    }

}
