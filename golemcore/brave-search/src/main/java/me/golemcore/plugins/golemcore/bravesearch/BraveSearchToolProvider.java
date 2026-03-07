package me.golemcore.plugins.golemcore.bravesearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class BraveSearchToolProvider implements ToolProvider {

    private static final String PARAM_QUERY = "query";
    private static final String PARAM_COUNT = "count";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_OBJECT = "object";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2000;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final BraveSearchPluginConfigService configService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BraveSearchToolProvider(BraveSearchPluginConfigService configService) {
        this.configService = configService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean isEnabled() {
        BraveSearchPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("brave_search")
                .description("Search the web using Brave Search. Returns titles, URLs, and descriptions.")
                .inputSchema(Map.of(
                        "type", TYPE_OBJECT,
                        "properties", Map.of(
                                PARAM_QUERY, Map.of(
                                        "type", TYPE_STRING,
                                        "description", "The search query"),
                                PARAM_COUNT, Map.of(
                                        "type", TYPE_INTEGER,
                                        "description", "Number of results to return (1-20)")),
                        "required", List.of(PARAM_QUERY)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            String query = parameters.get(PARAM_QUERY) instanceof String value ? value : null;
            if (query == null || query.isBlank()) {
                return ToolResult.failure("Search query is required");
            }
            if (!isEnabled()) {
                return ToolResult.failure("Brave Search is disabled or API key is missing");
            }

            int count = configService.getConfig().getDefaultCount();
            Object countObj = parameters.get(PARAM_COUNT);
            if (countObj instanceof Number number) {
                count = Math.max(1, Math.min(20, number.intValue()));
            }
            return executeWithRetry(query, count);
        });
    }

    private ToolResult executeWithRetry(String query, int count) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                BraveSearchResponse response = search(query, count);
                return buildSuccessResult(query, response);
            } catch (BraveSearchException e) {
                if (e.statusCode() == HTTP_TOO_MANY_REQUESTS && attempt < MAX_RETRIES) {
                    sleepBeforeRetry((long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attempt)));
                    continue;
                }
                if (e.statusCode() == HTTP_TOO_MANY_REQUESTS) {
                    return ToolResult.failure("Brave Search rate limit exceeded");
                }
                return ToolResult.failure("Brave Search request failed: " + e.getMessage());
            } catch (Exception e) {
                return ToolResult.failure("Brave Search request failed: " + e.getMessage());
            }
        }
        return ToolResult.failure("Brave Search request failed");
    }

    private BraveSearchResponse search(String query, int count) throws IOException {
        BraveSearchPluginConfig config = configService.getConfig();
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.search.brave.com")
                .addPathSegments("res/v1/web/search")
                .addQueryParameter("q", query)
                .addQueryParameter("count", Integer.toString(count))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", config.getApiKey())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute();
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new BraveSearchException(response.code(), "HTTP " + response.code());
            }
            return objectMapper.readValue(body.string(), BraveSearchResponse.class);
        }
    }

    private ToolResult buildSuccessResult(String query, BraveSearchResponse response) {
        if (response.web == null || response.web.results == null || response.web.results.isEmpty()) {
            return ToolResult.success("No results found for: " + query);
        }

        List<WebResult> results = response.web.results;
        String output = results.stream()
                .map(result -> String.format("**%s**%n%s%n%s",
                        result.title,
                        result.url,
                        result.description != null ? result.description : ""))
                .collect(Collectors.joining("\n\n"));
        String header = String.format("Search results for \"%s\" (%d results):%n%n", query, results.size());
        return ToolResult.success(header + output, Map.of(
                PARAM_QUERY, query,
                PARAM_COUNT, results.size(),
                "results", results.stream()
                        .map(result -> Map.of(
                                "title", result.title != null ? result.title : "",
                                "url", result.url != null ? result.url : "",
                                "description", result.description != null ? result.description : ""))
                        .toList()));
    }

    private void sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Brave Search retry interrupted", e);
        }
    }

    private static final class BraveSearchException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int httpStatusCode;

        private BraveSearchException(int statusCode, String message) {
            super(message);
            this.httpStatusCode = statusCode;
        }

        private int statusCode() {
            return httpStatusCode;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BraveSearchResponse {
        @JsonProperty("web")
        private WebResults web;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WebResults {
        @JsonProperty("results")
        private List<WebResult> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WebResult {
        @JsonProperty("title")
        private String title;

        @JsonProperty("url")
        private String url;

        @JsonProperty("description")
        private String description;
    }
}
