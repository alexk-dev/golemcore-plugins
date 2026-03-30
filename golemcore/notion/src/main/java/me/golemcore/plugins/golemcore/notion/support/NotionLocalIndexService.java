package me.golemcore.plugins.golemcore.notion.support;

import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotionLocalIndexService {

    private static final Pattern SPLIT_LINES = Pattern.compile("\\R");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern QUERY_TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}_-]{2,}");
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int CHUNK_MAX_CHARS = 1_500;

    private final NotionApiClient apiClient;
    private final NotionPluginConfigService configService;
    private final NotionStoragePaths storagePaths;
    private final NotionPathValidator pathValidator = new NotionPathValidator();

    public NotionLocalIndexService(
            NotionApiClient apiClient,
            NotionPluginConfigService configService,
            NotionStoragePaths storagePaths) {
        this.apiClient = apiClient;
        this.configService = configService;
        this.storagePaths = storagePaths;
    }

    public synchronized NotionReindexSummary reindexAll() {
        NotionPluginConfig config = configService.getConfig();
        if (config.getRootPageId() == null || config.getRootPageId().isBlank()) {
            throw new IllegalStateException("Root page ID is not configured.");
        }
        storagePaths.ensureStorageDirectories();
        List<IndexedPage> pages = new ArrayList<>();
        crawlPage(config.getRootPageId(), "", apiClient.retrievePageTitle(config.getRootPageId()), pages);
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            connection.setAutoCommit(false);
            clearIndex(connection);
            int chunkCount = 0;
            for (IndexedPage page : pages) {
                upsertPage(connection, page);
                List<IndexedChunk> chunks = chunkPage(page);
                chunkCount += chunks.size();
                insertChunks(connection, chunks);
            }
            connection.commit();
            return new NotionReindexSummary(pages.size(), chunkCount);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to rebuild Notion local index: " + ex.getMessage(), ex);
        }
    }

    public synchronized List<NotionSearchHit> search(String query, String pathPrefix, int limit) {
        String ftsQuery = toFtsQuery(query);
        if (ftsQuery.isBlank()) {
            return List.of();
        }
        if (!Files.exists(storagePaths.indexDatabasePath())) {
            return List.of();
        }
        String normalizedPathPrefix = pathValidator.normalizeNotePath(pathPrefix);
        int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            return executeSearch(connection, ftsQuery, normalizedPathPrefix, normalizedLimit, query);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query Notion local index: " + ex.getMessage(), ex);
        }
    }

    private void crawlPage(String pageId, String path, String title, List<IndexedPage> pages) {
        String safeTitle = title != null ? title : "";
        pages.add(new IndexedPage(
                pageId,
                path,
                safeTitle,
                apiClient.retrievePageMarkdown(pageId)));
        for (NotionPageSummary child : apiClient.listChildPages(pageId)) {
            String childPath = path.isBlank() ? child.title() : path + "/" + child.title();
            crawlPage(child.id(), childPath, child.title(), pages);
        }
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pages (
                        page_id TEXT PRIMARY KEY,
                        pseudo_path TEXT NOT NULL,
                        title TEXT NOT NULL,
                        plain_text TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS chunks (
                        chunk_id TEXT PRIMARY KEY,
                        page_id TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        heading_path TEXT NOT NULL,
                        plain_text TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(
                        chunk_id UNINDEXED,
                        page_id UNINDEXED,
                        pseudo_path,
                        title,
                        heading_path,
                        body
                    )
                    """);
        }
    }

    private void clearIndex(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM chunks_fts");
            statement.executeUpdate("DELETE FROM chunks");
            statement.executeUpdate("DELETE FROM pages");
        }
    }

    private void upsertPage(Connection connection, IndexedPage page) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO pages(page_id, pseudo_path, title, plain_text)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, page.pageId());
            statement.setString(2, page.path());
            statement.setString(3, page.title());
            statement.setString(4, normalizeText(page.markdown()));
            statement.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, List<IndexedChunk> chunks) throws SQLException {
        try (PreparedStatement chunkStatement = connection.prepareStatement("""
                INSERT INTO chunks(chunk_id, page_id, ordinal, heading_path, plain_text)
                VALUES (?, ?, ?, ?, ?)
                """);
                PreparedStatement ftsStatement = connection.prepareStatement("""
                        INSERT INTO chunks_fts(chunk_id, page_id, pseudo_path, title, heading_path, body)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
            for (IndexedChunk chunk : chunks) {
                chunkStatement.setString(1, chunk.chunkId());
                chunkStatement.setString(2, chunk.pageId());
                chunkStatement.setInt(3, chunk.ordinal());
                chunkStatement.setString(4, chunk.headingPath());
                chunkStatement.setString(5, chunk.body());
                chunkStatement.addBatch();

                ftsStatement.setString(1, chunk.chunkId());
                ftsStatement.setString(2, chunk.pageId());
                ftsStatement.setString(3, chunk.path());
                ftsStatement.setString(4, chunk.title());
                ftsStatement.setString(5, chunk.headingPath());
                ftsStatement.setString(6, chunk.body());
                ftsStatement.addBatch();
            }
            chunkStatement.executeBatch();
            ftsStatement.executeBatch();
        }
    }

    private List<IndexedChunk> chunkPage(IndexedPage page) {
        List<IndexedChunk> chunks = new ArrayList<>();
        String markdown = page.markdown() != null ? page.markdown() : "";
        if (markdown.isBlank()) {
            chunks.add(new IndexedChunk(
                    randomChunkId(),
                    page.pageId(),
                    page.path(),
                    page.title(),
                    page.title(),
                    0,
                    page.title()));
            return chunks;
        }

        Deque<String> headingStack = new ArrayDeque<>();
        StringBuilder body = new StringBuilder();
        int ordinal = 0;
        for (String line : SPLIT_LINES.split(markdown, -1)) {
            var headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                ordinal = flushChunk(page, chunks, headingStack, body, ordinal);
                int level = headingMatcher.group(1).length();
                while (headingStack.size() >= level) {
                    headingStack.removeLast();
                }
                headingStack.addLast(normalizeText(headingMatcher.group(2)));
                body.append(normalizeText(headingMatcher.group(2))).append('\n');
                continue;
            }
            if (body.length() >= CHUNK_MAX_CHARS && !normalizeText(body.toString()).isBlank()) {
                ordinal = flushChunk(page, chunks, headingStack, body, ordinal);
            }
            body.append(line).append('\n');
        }
        flushChunk(page, chunks, headingStack, body, ordinal);
        if (chunks.isEmpty()) {
            chunks.add(new IndexedChunk(
                    randomChunkId(),
                    page.pageId(),
                    page.path(),
                    page.title(),
                    page.title(),
                    0,
                    page.title()));
        }
        return chunks;
    }

    private int flushChunk(
            IndexedPage page,
            List<IndexedChunk> chunks,
            Deque<String> headingStack,
            StringBuilder body,
            int ordinal) {
        String normalizedBody = normalizeText(body.toString());
        body.setLength(0);
        if (normalizedBody.isBlank()) {
            return ordinal;
        }
        chunks.add(new IndexedChunk(
                randomChunkId(),
                page.pageId(),
                page.path(),
                page.title(),
                headingPath(headingStack, page.title()),
                ordinal,
                normalizedBody));
        return ordinal + 1;
    }

    private List<NotionSearchHit> executeSearch(
            Connection connection,
            String ftsQuery,
            String normalizedPathPrefix,
            int limit,
            String rawQuery) throws SQLException {
        String sql = """
                SELECT chunk_id, page_id, pseudo_path, title, heading_path, body
                FROM chunks_fts
                WHERE chunks_fts MATCH ?
                """
                + (normalizedPathPrefix.isBlank() ? "" : " AND (pseudo_path = ? OR pseudo_path LIKE ?)")
                + """
                        ORDER BY bm25(chunks_fts)
                        LIMIT ?
                        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index, ftsQuery);
            index++;
            if (!normalizedPathPrefix.isBlank()) {
                statement.setString(index, normalizedPathPrefix);
                index++;
                statement.setString(index, normalizedPathPrefix + "/%");
                index++;
            }
            statement.setInt(index, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NotionSearchHit> hits = new ArrayList<>();
                while (resultSet.next()) {
                    String body = resultSet.getString("body");
                    hits.add(new NotionSearchHit(
                            resultSet.getString("chunk_id"),
                            resultSet.getString("page_id"),
                            resultSet.getString("pseudo_path"),
                            resultSet.getString("title"),
                            buildSnippet(body, rawQuery),
                            resultSet.getString("heading_path")));
                }
                return hits;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + storagePaths.indexDatabasePath());
    }

    private String toFtsQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        Set<String> tokens = new LinkedHashSet<>();
        var matcher = QUERY_TOKEN_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        if (tokens.isEmpty()) {
            return "";
        }
        return tokens.stream()
                .map(token -> "\"" + token.replace("\"", "\"\"") + "\"")
                .reduce((left, right) -> left + " AND " + right)
                .orElse("");
    }

    private String buildSnippet(String body, String query) {
        String normalizedBody = normalizeText(body);
        if (normalizedBody.isBlank()) {
            return "";
        }
        String lowerBody = normalizedBody.toLowerCase(Locale.ROOT);
        int anchor = Integer.MAX_VALUE;
        var matcher = QUERY_TOKEN_PATTERN.matcher(query != null ? query.toLowerCase(Locale.ROOT) : "");
        while (matcher.find()) {
            int position = lowerBody.indexOf(matcher.group());
            if (position >= 0) {
                anchor = Math.min(anchor, position);
            }
        }
        if (anchor == Integer.MAX_VALUE) {
            anchor = 0;
        }
        int start = Math.max(0, anchor - 40);
        int end = Math.min(normalizedBody.length(), start + 180);
        String snippet = normalizedBody.substring(start, end).trim();
        if (start > 0) {
            snippet = "... " + snippet;
        }
        if (end < normalizedBody.length()) {
            snippet = snippet + " ...";
        }
        return snippet;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String headingPath(Deque<String> headingStack, String fallbackTitle) {
        if (headingStack.isEmpty()) {
            return fallbackTitle != null ? fallbackTitle : "";
        }
        return String.join(" / ", headingStack);
    }

    private String randomChunkId() {
        return UUID.randomUUID().toString();
    }

    private record IndexedPage(String pageId, String path, String title, String markdown) {
    }

    private record IndexedChunk(
            String chunkId,
            String pageId,
            String path,
            String title,
            String headingPath,
            int ordinal,
            String body) {
    }
}
