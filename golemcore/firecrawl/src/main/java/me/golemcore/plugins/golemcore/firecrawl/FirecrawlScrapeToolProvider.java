package me.golemcore.plugins.golemcore.firecrawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FirecrawlScrapeToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_URL = "url";
    private static final String PARAM_FORMAT = "format";
    private static final String PARAM_ONLY_MAIN_CONTENT = "only_main_content";
    private static final String PARAM_MAX_AGE_MS = "max_age_ms";
    private static final String PARAM_WAIT_FOR_MS = "wait_for_ms";
    private static final String PARAM_TIMEOUT_MS = "timeout_ms";
    private static final String DEFAULT_FORMAT = "markdown";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 300_000;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final int MAX_OUTPUT_CHARS = 12_000;
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("markdown", "summary", "html", "rawHtml", "links");

    private final FirecrawlPluginConfigService configService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isEnabled() {
        FirecrawlPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && hasText(config.getApiKey());
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("firecrawl_scrape")
                .description("Scrape and normalize a webpage through Firecrawl.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_URL, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Absolute HTTP(S) URL to scrape."),
                                PARAM_FORMAT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Desired output format: markdown, summary, html, rawHtml, or links."),
                                PARAM_ONLY_MAIN_CONTENT, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Whether to keep only the main article content."),
                                PARAM_MAX_AGE_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Maximum cache age in milliseconds."),
                                PARAM_WAIT_FOR_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional wait time in milliseconds before capture."),
                                PARAM_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Request timeout in milliseconds.")),
                        REQUIRED, List.of(PARAM_URL)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeScrape(parameters));
    }

    protected Response executeRequest(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }

    protected void sleepBeforeRetry(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firecrawl retry interrupted", ex);
        }
    }

    private ToolResult executeScrape(Map<String, Object> parameters) {
        String url = readString(parameters.get(PARAM_URL));
        if (!hasText(url)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Scrape URL is required");
        }
        if (!isEnabled()) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Firecrawl is disabled or API key is missing");
        }

        FirecrawlPluginConfig config = configService.getConfig();
        String format = normalizeFormat(readString(parameters.get(PARAM_FORMAT)), config.getDefaultFormat());
        boolean onlyMainContent = readBoolean(parameters.get(PARAM_ONLY_MAIN_CONTENT), config.getOnlyMainContent());
        int maxAgeMs = readInteger(parameters.get(PARAM_MAX_AGE_MS), config.getMaxAgeMs(), 0, Integer.MAX_VALUE);
        int waitForMs = readInteger(parameters.get(PARAM_WAIT_FOR_MS), 0, 0, MAX_TIMEOUT_MS);
        int timeoutMs = readInteger(parameters.get(PARAM_TIMEOUT_MS), config.getTimeoutMs(), 1_000, MAX_TIMEOUT_MS);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                FirecrawlScrapeResult response = scrape(url, format, onlyMainContent, maxAgeMs, waitForMs, timeoutMs);
                return buildSuccessResult(url, format, response);
            } catch (FirecrawlRequestException ex) {
                if (shouldRetry(ex.statusCode()) && attempt < MAX_RETRIES - 1) {
                    sleepBeforeRetry((long) (INITIAL_BACKOFF_MS * Math.pow(2, attempt)));
                    continue;
                }
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, mapErrorMessage(ex.statusCode(), ex));
            } catch (Exception ex) { // NOSONAR - tool I/O should degrade gracefully
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                        "Firecrawl request failed: " + ex.getMessage());
            }
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Firecrawl request failed");
    }

    private FirecrawlScrapeResult scrape(
            String url,
            String format,
            boolean onlyMainContent,
            int maxAgeMs,
            int waitForMs,
            int timeoutMs) throws IOException {
        FirecrawlPluginConfig config = configService.getConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put(PARAM_URL, url);
        requestBody.put("formats", List.of(format));
        requestBody.put("onlyMainContent", onlyMainContent);
        requestBody.put("maxAge", maxAgeMs);
        requestBody.put("waitFor", waitForMs);
        requestBody.put("timeout", timeoutMs);

        Request request = new Request.Builder()
                .url("https://api.firecrawl.dev/v2/scrape")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), APPLICATION_JSON))
                .build();

        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new FirecrawlRequestException(response.code(), "HTTP " + response.code());
            }
            JsonNode root = objectMapper.readTree(body.string());
            if (root.has("success") && !root.path("success").asBoolean(true)) {
                throw new FirecrawlRequestException(500, "Firecrawl returned unsuccessful response");
            }
            return parseScrapeResult(root, format, url);
        }
    }

    private FirecrawlScrapeResult parseScrapeResult(JsonNode root, String format, String fallbackUrl) {
        JsonNode data = root.path("data");
        JsonNode metadata = data.path("metadata");
        String title = readNodeText(metadata, "title");
        String sourceUrl = firstText(readNodeText(metadata, "sourceURL"), fallbackUrl);
        Integer statusCode = metadata.has("statusCode") ? metadata.path("statusCode").asInt() : null;
        String warning = readNodeText(root, "warning");
        List<String> links = readStringList(data.path("links"));
        String content = resolveContent(data, format, links);
        return new FirecrawlScrapeResult(sourceUrl, title, format, content, warning, statusCode, links);
    }

    private ToolResult buildSuccessResult(String url, String format, FirecrawlScrapeResult result) {
        StringBuilder output = new StringBuilder();
        output.append("Firecrawl scrape for ")
                .append(result.url() != null ? result.url() : url)
                .append(" (format: ")
                .append(format)
                .append(")\n");
        if (hasText(result.title())) {
            output.append("Title: ").append(result.title()).append('\n');
        }
        if (result.statusCode() != null) {
            output.append("HTTP Status: ").append(result.statusCode()).append('\n');
        }
        if (hasText(result.warning())) {
            output.append("Warning: ").append(result.warning()).append('\n');
        }
        if (hasText(result.content())) {
            output.append('\n')
                    .append(truncate(result.content(), MAX_OUTPUT_CHARS));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", result.url());
        data.put("title", result.title() != null ? result.title() : "");
        data.put("format", result.format());
        data.put("content", result.content() != null ? result.content() : "");
        data.put("warning", result.warning() != null ? result.warning() : "");
        data.put("status_code", result.statusCode());
        data.put("links", result.links());
        return ToolResult.success(output.toString().trim(), data);
    }

    private String mapErrorMessage(int statusCode, FirecrawlRequestException exception) {
        return switch (statusCode) {
        case 401, 403 -> "Firecrawl authentication failed";
        case 402 -> "Firecrawl credits exhausted or billing is required";
        case 429 -> "Firecrawl rate limit exceeded";
        default -> "Firecrawl request failed: " + exception.getMessage();
        };
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String resolveContent(JsonNode data, String format, List<String> links) {
        return switch (format) {
        case "summary" -> readNodeText(data, "summary");
        case "html" -> readNodeText(data, "html");
        case "rawHtml" -> readNodeText(data, "rawHtml");
        case "links" -> String.join("\n", links);
        default -> readNodeText(data, "markdown");
        };
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item != null && item.isTextual() && hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private String normalizeFormat(String value, String defaultValue) {
        if (value != null && SUPPORTED_FORMATS.contains(value)) {
            return value;
        }
        if (defaultValue != null && SUPPORTED_FORMATS.contains(defaultValue)) {
            return defaultValue;
        }
        return DEFAULT_FORMAT;
    }

    private int readInteger(Object value, int defaultValue, int min, int max) {
        int normalizedDefault = Math.max(min, Math.min(max, defaultValue));
        if (value instanceof Number number) {
            return Math.max(min, Math.min(max, number.intValue()));
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(text.trim())));
            } catch (NumberFormatException ignored) {
                return normalizedDefault;
            }
        }
        return normalizedDefault;
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String readString(Object value) {
        return value instanceof String text ? text.trim() : null;
    }

    private String readNodeText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private String firstText(String first, String fallback) {
        return hasText(first) ? first : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class FirecrawlRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int statusCode;

        private FirecrawlRequestException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    private record FirecrawlScrapeResult(
            String url,
            String title,
            String format,
            String content,
            String warning,
            Integer statusCode,
            List<String> links) {
    }
}
