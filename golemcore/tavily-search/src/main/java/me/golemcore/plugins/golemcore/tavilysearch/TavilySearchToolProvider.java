package me.golemcore.plugins.golemcore.tavilysearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TavilySearchToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_ARRAY = "array";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ITEMS = "items";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_MAX_RESULTS = "max_results";
    private static final String PARAM_TOPIC = "topic";
    private static final String PARAM_SEARCH_DEPTH = "search_depth";
    private static final String PARAM_INCLUDE_ANSWER = "include_answer";
    private static final String PARAM_INCLUDE_RAW_CONTENT = "include_raw_content";
    private static final String PARAM_INCLUDE_DOMAINS = "include_domains";
    private static final String PARAM_EXCLUDE_DOMAINS = "exclude_domains";
    private static final String DEFAULT_TOPIC = "general";
    private static final String DEFAULT_SEARCH_DEPTH = "basic";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final int MAX_RAW_CONTENT_RESULTS = 3;
    private static final int MAX_RAW_CONTENT_CHARS = 1_200;
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final Set<String> SUPPORTED_TOPICS = Set.of("general", "news");
    private static final Set<String> SUPPORTED_SEARCH_DEPTHS = Set.of("basic", "advanced");

    private final TavilySearchPluginConfigService configService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isEnabled() {
        TavilySearchPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && hasText(config.getApiKey());
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("tavily_search")
                .description("Search the web with Tavily and return grounded answers plus cited results.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_QUERY, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "The search query to send to Tavily."),
                                PARAM_MAX_RESULTS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Maximum number of results to return (1-20)."),
                                PARAM_TOPIC, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Search topic: general or news."),
                                PARAM_SEARCH_DEPTH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Search depth: basic or advanced."),
                                PARAM_INCLUDE_ANSWER, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Whether Tavily should return a synthesized answer."),
                                PARAM_INCLUDE_RAW_CONTENT, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Whether Tavily should fetch raw page content."),
                                PARAM_INCLUDE_DOMAINS, Map.of(
                                        TYPE, TYPE_ARRAY,
                                        ITEMS, Map.of(TYPE, TYPE_STRING),
                                        "description", "Optional allowlist of domains to include."),
                                PARAM_EXCLUDE_DOMAINS, Map.of(
                                        TYPE, TYPE_ARRAY,
                                        ITEMS, Map.of(TYPE, TYPE_STRING),
                                        "description", "Optional denylist of domains to exclude.")),
                        REQUIRED, List.of(PARAM_QUERY)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeSearch(parameters));
    }

    protected Response executeRequest(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }

    protected void sleepBeforeRetry(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tavily retry interrupted", ex);
        }
    }

    private ToolResult executeSearch(Map<String, Object> parameters) {
        String query = readString(parameters.get(PARAM_QUERY));
        if (!hasText(query)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Search query is required");
        }
        if (!isEnabled()) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Tavily Search is disabled or API key is missing");
        }

        TavilySearchPluginConfig config = configService.getConfig();
        int maxResults = readInteger(parameters.get(PARAM_MAX_RESULTS), config.getDefaultMaxResults());
        String topic = normalizeTopic(readString(parameters.get(PARAM_TOPIC)), config.getDefaultTopic());
        String searchDepth = normalizeSearchDepth(readString(parameters.get(PARAM_SEARCH_DEPTH)),
                config.getDefaultSearchDepth());
        boolean includeAnswer = readBoolean(parameters.get(PARAM_INCLUDE_ANSWER), config.getIncludeAnswer());
        boolean includeRawContent = readBoolean(parameters.get(PARAM_INCLUDE_RAW_CONTENT),
                config.getIncludeRawContent());
        List<String> includeDomains = readStringList(parameters.get(PARAM_INCLUDE_DOMAINS));
        List<String> excludeDomains = readStringList(parameters.get(PARAM_EXCLUDE_DOMAINS));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                TavilySearchResponse response = search(query, maxResults, topic, searchDepth, includeAnswer,
                        includeRawContent, includeDomains, excludeDomains);
                return buildSuccessResult(query, maxResults, topic, searchDepth, includeRawContent, response);
            } catch (TavilySearchException ex) {
                if (shouldRetry(ex.statusCode()) && attempt < MAX_RETRIES - 1) {
                    sleepBeforeRetry((long) (INITIAL_BACKOFF_MS * Math.pow(2, attempt)));
                    continue;
                }
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, mapErrorMessage(ex.statusCode(), ex));
            } catch (Exception ex) { // NOSONAR - tool I/O should degrade gracefully
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                        "Tavily Search request failed: " + ex.getMessage());
            }
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Tavily Search request failed");
    }

    private TavilySearchResponse search(
            String query,
            int maxResults,
            String topic,
            String searchDepth,
            boolean includeAnswer,
            boolean includeRawContent,
            List<String> includeDomains,
            List<String> excludeDomains) throws IOException {
        TavilySearchPluginConfig config = configService.getConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put(PARAM_QUERY, query);
        requestBody.put(PARAM_TOPIC, topic);
        requestBody.put(PARAM_SEARCH_DEPTH, searchDepth);
        requestBody.put(PARAM_MAX_RESULTS, maxResults);
        requestBody.put(PARAM_INCLUDE_ANSWER, includeAnswer);
        requestBody.put(PARAM_INCLUDE_RAW_CONTENT, includeRawContent);
        if (!includeDomains.isEmpty()) {
            requestBody.put(PARAM_INCLUDE_DOMAINS, includeDomains);
        }
        if (!excludeDomains.isEmpty()) {
            requestBody.put(PARAM_EXCLUDE_DOMAINS, excludeDomains);
        }

        Request request = new Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), APPLICATION_JSON))
                .build();

        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new TavilySearchException(response.code(), "HTTP " + response.code());
            }
            return objectMapper.readValue(body.string(), TavilySearchResponse.class);
        }
    }

    private ToolResult buildSuccessResult(
            String query,
            int maxResults,
            String topic,
            String searchDepth,
            boolean includeRawContent,
            TavilySearchResponse response) {
        List<TavilySearchResult> results = response.getResults() != null ? response.getResults() : List.of();
        boolean hasAnswer = hasText(response.getAnswer());
        if (results.isEmpty() && !hasAnswer) {
            return ToolResult.success("No results found for: " + query, Map.of(
                    PARAM_QUERY, query,
                    PARAM_MAX_RESULTS, 0,
                    "results", List.of()));
        }

        StringBuilder output = new StringBuilder();
        output.append(String.format("Tavily search results for \"%s\" (%d results, topic=%s, depth=%s)%n%n",
                query,
                results.size(),
                topic,
                searchDepth));
        if (hasAnswer) {
            output.append("Answer:\n")
                    .append(response.getAnswer())
                    .append("\n\n");
        }

        boolean rawContentOmitted = false;
        for (int index = 0; index < results.size(); index++) {
            TavilySearchResult result = results.get(index);
            output.append(index + 1)
                    .append(". **")
                    .append(hasText(result.getTitle()) ? result.getTitle() : result.getUrl())
                    .append("**\n");
            if (hasText(result.getUrl())) {
                output.append(result.getUrl()).append('\n');
            }
            if (result.getScore() != null) {
                output.append(String.format("Relevance: %.2f%n", result.getScore()));
            }
            if (hasText(result.getPublishedDate())) {
                output.append("Published: ").append(result.getPublishedDate()).append('\n');
            }
            if (hasText(result.getContent())) {
                output.append(result.getContent()).append('\n');
            }
            if (includeRawContent && hasText(result.getRawContent()) && index < MAX_RAW_CONTENT_RESULTS) {
                output.append("\nRaw content excerpt:\n")
                        .append(truncate(result.getRawContent(), MAX_RAW_CONTENT_CHARS))
                        .append('\n');
            } else if (includeRawContent && hasText(result.getRawContent())) {
                rawContentOmitted = true;
            }
            output.append('\n');
        }
        if (rawContentOmitted) {
            output.append("Additional raw content was omitted to keep the tool response manageable.\n");
        }

        List<Map<String, Object>> mappedResults = results.stream()
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", defaultString(result.getTitle()));
                    item.put("url", defaultString(result.getUrl()));
                    item.put("content", defaultString(result.getContent()));
                    item.put("raw_content", defaultString(result.getRawContent()));
                    item.put("published_date", defaultString(result.getPublishedDate()));
                    item.put("favicon", defaultString(result.getFavicon()));
                    item.put("score", result.getScore());
                    return item;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(PARAM_QUERY, query);
        data.put(PARAM_MAX_RESULTS, results.size());
        data.put(PARAM_TOPIC, topic);
        data.put(PARAM_SEARCH_DEPTH, searchDepth);
        data.put("answer", defaultString(response.getAnswer()));
        data.put("response_time", response.getResponseTime());
        data.put("results", mappedResults);
        data.put("requested_max_results", maxResults);
        return ToolResult.success(output.toString().trim(), data);
    }

    private String mapErrorMessage(int statusCode, TavilySearchException exception) {
        return switch (statusCode) {
        case 401, 403 -> "Tavily Search authentication failed";
        case 429 -> "Tavily Search rate limit exceeded";
        default -> "Tavily Search request failed: " + exception.getMessage();
        };
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String normalizeTopic(String value, String defaultValue) {
        if (value != null && SUPPORTED_TOPICS.contains(value)) {
            return value;
        }
        if (defaultValue != null && SUPPORTED_TOPICS.contains(defaultValue)) {
            return defaultValue;
        }
        return DEFAULT_TOPIC;
    }

    private String normalizeSearchDepth(String value, String defaultValue) {
        if (value != null && SUPPORTED_SEARCH_DEPTHS.contains(value)) {
            return value;
        }
        if (defaultValue != null && SUPPORTED_SEARCH_DEPTHS.contains(defaultValue)) {
            return defaultValue;
        }
        return DEFAULT_SEARCH_DEPTH;
    }

    private List<String> readStringList(Object value) {
        Set<String> normalized = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = readString(item);
                if (hasText(text)) {
                    normalized.add(text.trim());
                }
            }
        } else if (value instanceof String text) {
            String[] parts = text.split("[,\\n]");
            for (String part : parts) {
                if (hasText(part)) {
                    normalized.add(part.trim());
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private int readInteger(Object value, int defaultValue) {
        int normalizedDefault = Math.max(1, Math.min(MAX_RESULTS, defaultValue));
        if (value instanceof Number number) {
            return Math.max(1, Math.min(MAX_RESULTS, number.intValue()));
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Math.max(1, Math.min(MAX_RESULTS, Integer.parseInt(text.trim())));
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

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultString(String value) {
        return value != null ? value : "";
    }

    private static final class TavilySearchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int httpStatusCode;

        private TavilySearchException(int statusCode, String message) {
            super(message);
            this.httpStatusCode = statusCode;
        }

        private int statusCode() {
            return httpStatusCode;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TavilySearchResponse {

        @JsonProperty("answer")
        private String answer;

        @JsonProperty("results")
        private List<TavilySearchResult> results;

        @JsonProperty("response_time")
        private Double responseTime;

        public String getAnswer() {
            return answer;
        }

        public List<TavilySearchResult> getResults() {
            return results;
        }

        public Double getResponseTime() {
            return responseTime;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TavilySearchResult {

        @JsonProperty("title")
        private String title;

        @JsonProperty("url")
        private String url;

        @JsonProperty("content")
        private String content;

        @JsonProperty("raw_content")
        private String rawContent;

        @JsonProperty("published_date")
        private String publishedDate;

        @JsonProperty("favicon")
        private String favicon;

        @JsonProperty("score")
        private Double score;

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getContent() {
            return content;
        }

        public String getRawContent() {
            return rawContent;
        }

        public String getPublishedDate() {
            return publishedDate;
        }

        public String getFavicon() {
            return favicon;
        }

        public Double getScore() {
            return score;
        }
    }
}
