package me.golemcore.plugins.golemcore.brain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class BrainService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final Pattern SLUG_SEPARATOR_PATTERN = Pattern.compile("[^a-z0-9]+");

    private final BrainPluginConfigService configService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BrainService(BrainPluginConfigService configService) {
        this(configService, new OkHttpClient());
    }

    public BrainService(BrainPluginConfigService configService, OkHttpClient httpClient) {
        this.configService = configService;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isAvailable() {
        BrainPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && config.getBaseUrl() != null
                && !config.getBaseUrl().isBlank();
    }

    public ToolResult listSpaces() {
        try {
            JsonNode node = executeJson("GET", apiUrl("/api/spaces"), null);
            int count = node.isArray() ? node.size() : 0;
            return ToolResult.success("Listed " + count + " Brain space(s)", nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult listTree(String spaceSlug) {
        String space = resolveSpace(spaceSlug);
        try {
            JsonNode node = executeJson("GET", spaceUrl(space, "/tree"), null);
            return ToolResult.success("Loaded Brain tree for space " + space, nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult searchPages(String spaceSlug, String query, Integer limit) {
        String resolvedQuery = requireText(query, "query");
        String space = resolveSpace(spaceSlug);
        int resolvedLimit = resolveLimit(limit, DEFAULT_SEARCH_LIMIT);
        try {
            JsonNode node = executeJson("GET", searchUrl(space, resolvedQuery, resolvedLimit), null);
            return ToolResult.success(formatSearchResults(resolvedQuery, node), nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult intellisearch(
            String spaceSlug,
            String context,
            String query,
            Integer limit,
            String dynamicApiSlug,
            Boolean useDynamicEndpoint) {
        String resolvedContext = requireText(firstNonBlank(context, query), "context or query");
        String resolvedQuery = firstNonBlank(query, context);
        String space = resolveSpace(spaceSlug);
        int resolvedLimit = resolveLimit(limit, configService.getConfig().getDefaultIntellisearchLimit());
        String resolvedDynamicApiSlug = trimToNull(dynamicApiSlug);
        if (resolvedDynamicApiSlug == null) {
            resolvedDynamicApiSlug = trimToNull(configService.getConfig().getDynamicApiSlug());
        }
        boolean shouldUseDynamicEndpoint = !Boolean.FALSE.equals(useDynamicEndpoint) && resolvedDynamicApiSlug != null;
        if (shouldUseDynamicEndpoint) {
            return runDynamicIntellisearch(space, resolvedDynamicApiSlug, resolvedContext, resolvedQuery,
                    resolvedLimit);
        }
        return fallbackIntellisearch(space, resolvedContext, resolvedQuery, resolvedLimit);
    }

    public ToolResult readPage(String spaceSlug, String path) {
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        try {
            JsonNode node = executeJson("GET", pageUrl(space, pagePath), null);
            String content = node.path("content").asText(node.toPrettyString());
            return ToolResult.success(content, nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult createPage(String spaceSlug, String parentPath, String title, String slug, String content,
            String kind) {
        requireWriteAllowed();
        String space = resolveSpace(spaceSlug);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("parentPath", resolveSectionPath(space, parentPath));
            payload.put("title", requireText(title, "title"));
            payload.put("slug", trimToNull(slug));
            payload.put("content", valueOrEmpty(content));
            payload.put("kind", trimToNull(kind) != null ? kind : "PAGE");
            JsonNode node = executeJson("POST", spaceUrl(space, "/pages"), payload);
            return ToolResult.success("Created Brain page " + node.path("path").asText(""), nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult updatePage(String spaceSlug, String path, String title, String slug, String content,
            String revision) {
        requireWriteAllowed();
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", trimToNull(title));
        payload.put("slug", trimToNull(slug));
        payload.put("content", valueOrEmpty(content));
        payload.put("revision", trimToNull(revision));
        try {
            JsonNode node = executeJson("PUT", pageUrl(space, pagePath), payload);
            return ToolResult.success("Updated Brain page " + node.path("path").asText(pagePath), nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult deletePage(String spaceSlug, String path) {
        requireWriteAllowed();
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        try {
            executeNoContent("DELETE", pageUrl(space, pagePath), null);
            return ToolResult.success("Deleted Brain page " + pagePath, Map.of("path", pagePath));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult ensurePage(String spaceSlug, String path, String targetTitle) {
        requireWriteAllowed();
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", pagePath);
        payload.put("targetTitle", trimToNull(targetTitle));
        try {
            JsonNode node = executeJson("POST", spaceUrl(space, "/pages/ensure"), payload);
            return ToolResult.success("Ensured Brain page " + node.path("path").asText(pagePath), nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult movePage(String spaceSlug, String path, String targetParentPath, String targetSlug,
            String beforeSlug) {
        requireWriteAllowed();
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetParentPath", valueOrEmpty(targetParentPath));
        payload.put("targetSlug", trimToNull(targetSlug));
        payload.put("beforeSlug", trimToNull(beforeSlug));
        try {
            JsonNode node = executeJson("POST", pageActionUrl(space, pagePath, "/page/move"), payload);
            return ToolResult.success("Moved Brain page " + pagePath, nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult copyPage(String spaceSlug, String path, String targetParentPath, String targetSlug,
            String beforeSlug) {
        requireWriteAllowed();
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetParentPath", valueOrEmpty(targetParentPath));
        payload.put("targetSlug", trimToNull(targetSlug));
        payload.put("beforeSlug", trimToNull(beforeSlug));
        try {
            JsonNode node = executeJson("POST", pageActionUrl(space, pagePath, "/page/copy"), payload);
            return ToolResult.success("Copied Brain page " + pagePath, nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    public ToolResult listAssets(String spaceSlug, String path) {
        String pagePath = requireText(path, "path");
        String space = resolveSpace(spaceSlug);
        try {
            JsonNode node = executeJson("GET", pageActionUrl(space, pagePath, "/pages/assets"), null);
            return ToolResult.success("Listed Brain assets for " + pagePath, nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    private ToolResult runDynamicIntellisearch(String space, String dynamicApiSlug, String context, String query,
            int limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("context", context);
        payload.put("query", query);
        payload.put("limit", limit);
        payload.put("task", "Find the most relevant Brain documents for the provided context.");
        try {
            JsonNode node = executeJson("POST", dynamicApiUrl(space, dynamicApiSlug), payload);
            return ToolResult.success("Brain dynamic intellisearch via " + dynamicApiSlug + ": "
                    + compactJson(node), nodeToData(node));
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    private String resolveSectionPath(String space, String parentPath) throws IOException {
        String requestedPath = valueOrEmpty(parentPath);
        if (requestedPath.isBlank()) {
            return "";
        }
        Optional<String> exactPath = findSectionPath(space, requestedPath, true);
        if (exactPath.isPresent()) {
            return exactPath.get();
        }
        String slugifiedPath = slugifyPath(requestedPath);
        if (!slugifiedPath.isBlank() && !slugifiedPath.equals(requestedPath)) {
            Optional<String> slugPath = findSectionPath(space, slugifiedPath, false);
            if (slugPath.isPresent()) {
                return slugPath.get();
            }
        }
        throw new IllegalStateException("Brain section not found: " + requestedPath
                + " (also tried: " + slugifiedPath + ")");
    }

    private Optional<String> findSectionPath(String space, String path, boolean failWhenPage) throws IOException {
        try {
            JsonNode node = executeJson("GET", pageUrl(space, path), null);
            if (isSectionNode(node)) {
                String resolvedPath = node.path("path").asText(path);
                return Optional.of(resolvedPath);
            }
            if (failWhenPage && "PAGE".equals(node.path("kind").asText(""))) {
                throw new IllegalStateException("Brain path is not a section: " + path);
            }
            return Optional.empty();
        } catch (BrainApiException exception) {
            if (exception.getHttpStatusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private boolean isSectionNode(JsonNode node) {
        String kind = node.path("kind").asText("");
        return "SECTION".equals(kind) || "ROOT".equals(kind);
    }

    private String slugifyPath(String path) {
        String normalized = path.replace('\\', '/').trim()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (normalized.isBlank()) {
            return "";
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            String slug = slugifySegment(segment);
            if (!slug.isBlank()) {
                segments.add(slug);
            }
        }
        return String.join("/", segments);
    }

    private String slugifySegment(String segment) {
        String slug = SLUG_SEPARATOR_PATTERN.matcher(segment.trim().toLowerCase(Locale.ROOT)).replaceAll("-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return slug;
    }

    private ToolResult fallbackIntellisearch(String space, String context, String query, int limit) {
        try {
            JsonNode hits = executeJson("GET", searchUrl(space, query, limit), null);
            List<Map<String, Object>> documents = new ArrayList<>();
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    String path = hit.path("path").asText(null);
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    JsonNode page = executeJson("GET", pageUrl(space, path), null);
                    Map<String, Object> document = new LinkedHashMap<>();
                    document.put("path", path);
                    document.put("title", page.path("title").asText(hit.path("title").asText("")));
                    document.put("excerpt", hit.path("excerpt").asText(""));
                    document.put("content", page.path("content").asText(""));
                    documents.add(document);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("context", context);
            data.put("query", query);
            data.put("documents", documents);
            return ToolResult.success(formatIntellisearchDocuments(documents), data);
        } catch (IOException | IllegalStateException exception) {
            return executionFailure(exception.getMessage());
        }
    }

    private JsonNode executeJson(String method, HttpUrl url, Map<String, Object> payload) throws IOException {
        try (Response response = httpClient.newCall(buildRequest(method, url, payload)).execute();
                ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new BrainApiException(response.code(), responseMessage(responseBody));
            }
            if (responseBody.isBlank()) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }

    private void executeNoContent(String method, HttpUrl url, Map<String, Object> payload) throws IOException {
        try (Response response = httpClient.newCall(buildRequest(method, url, payload)).execute();
                ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new BrainApiException(response.code(), responseMessage(responseBody));
            }
        }
    }

    private Request buildRequest(String method, HttpUrl url, Map<String, Object> payload)
            throws JsonProcessingException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json");
        String apiToken = configService.getConfig().getApiToken();
        if (apiToken != null && !apiToken.isBlank()) {
            builder.header("Authorization", "Bearer " + apiToken);
        }
        RequestBody body = payload == null ? null : RequestBody.create(objectMapper.writeValueAsString(payload), JSON);
        return builder.method(method, body).build();
    }

    private HttpUrl apiUrl(String path) {
        HttpUrl base = parseBaseUrl();
        return base.newBuilder()
                .encodedPath(path)
                .build();
    }

    private HttpUrl spaceUrl(String space, String path) {
        return apiUrl("/api/spaces/" + encodePathSegment(space) + path);
    }

    private HttpUrl pageUrl(String space, String path) {
        return spaceUrl(space, "/page").newBuilder()
                .addQueryParameter("path", path)
                .build();
    }

    private HttpUrl pageActionUrl(String space, String path, String actionPath) {
        return spaceUrl(space, actionPath).newBuilder()
                .addQueryParameter("path", path)
                .build();
    }

    private HttpUrl searchUrl(String space, String query, int limit) {
        return spaceUrl(space, "/search").newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", Integer.toString(limit))
                .build();
    }

    private HttpUrl dynamicApiUrl(String space, String dynamicApiSlug) {
        return spaceUrl(space, "/dynamic-apis/" + encodePathSegment(dynamicApiSlug) + "/run");
    }

    private HttpUrl parseBaseUrl() {
        String baseUrl = requireText(configService.getConfig().getBaseUrl(), "baseUrl");
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException("Invalid Brain base URL: " + baseUrl);
        }
        return parsed;
    }

    private String resolveSpace(String spaceSlug) {
        return requireText(firstNonBlank(spaceSlug, configService.getConfig().getDefaultSpaceSlug()), "space_slug");
    }

    private int resolveLimit(Integer value, int defaultValue) {
        int limit = value != null && value > 0 ? value : defaultValue;
        return Math.max(1, Math.min(20, limit));
    }

    private void requireWriteAllowed() {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            throw new IllegalStateException("Brain write operations are disabled in plugin settings");
        }
    }

    private Object nodeToData(JsonNode node) throws JsonProcessingException {
        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private String formatSearchResults(String query, JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return "No Brain pages found for: " + query;
        }
        List<String> rows = new ArrayList<>();
        for (JsonNode hit : node) {
            rows.add("- " + hit.path("title").asText(hit.path("path").asText(""))
                    + " (`" + hit.path("path").asText("") + "`): "
                    + hit.path("excerpt").asText(""));
        }
        return "Brain search results for \"" + query + "\":\n" + String.join("\n", rows);
    }

    private String formatIntellisearchDocuments(List<Map<String, Object>> documents) {
        if (documents.isEmpty()) {
            return "Brain fallback intellisearch returned 0 document(s)";
        }
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            rows.add("- " + document.getOrDefault("title", "")
                    + " (`" + document.getOrDefault("path", "") + "`): "
                    + document.getOrDefault("content", ""));
        }
        return "Brain fallback intellisearch returned " + documents.size() + " document(s):\n"
                + String.join("\n", rows);
    }

    private String compactJson(JsonNode node) throws JsonProcessingException {
        return objectMapper.writeValueAsString(nodeToData(node));
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                message != null ? message : "Brain API request failed");
    }

    private String responseMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String extracted = extractErrorMessage(body);
        return extracted.isBlank() ? ": " + body.trim() : ": " + extracted;
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String error = node.path("error").asText("");
            if (!error.isBlank()) {
                return error;
            }
            String message = node.path("message").asText("");
            return message.isBlank() ? "" : message;
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private String requireText(String value, String fieldName) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text;
    }

    private String firstNonBlank(String first, String second) {
        String firstText = trimToNull(first);
        return firstText != null ? firstText : trimToNull(second);
    }

    private String valueOrEmpty(String value) {
        String text = trimToNull(value);
        return text != null ? text : "";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String encodePathSegment(String value) {
        return value.replace("/", "%2F");
    }

    private static class BrainApiException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private final int httpStatusCode;

        BrainApiException(int httpStatusCode, String responseMessage) {
            super("Brain API request failed with HTTP " + httpStatusCode + responseMessage);
            this.httpStatusCode = httpStatusCode;
        }

        int getHttpStatusCode() {
            return httpStatusCode;
        }
    }

}
