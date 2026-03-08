package me.golemcore.plugins.golemcore.firecrawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirecrawlScrapeToolProviderTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private FirecrawlPluginConfigService configService;
    private TestableFirecrawlScrapeToolProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        configService = mock(FirecrawlPluginConfigService.class);
        objectMapper = new ObjectMapper();
        FirecrawlPluginConfig config = FirecrawlPluginConfig.builder()
                .enabled(true)
                .apiKey("fc-test-key")
                .defaultFormat("markdown")
                .onlyMainContent(true)
                .maxAgeMs(60000)
                .timeoutMs(30000)
                .build();
        when(configService.getConfig()).thenReturn(config);
        provider = new TestableFirecrawlScrapeToolProvider(configService);
    }

    @Test
    void shouldScrapeMarkdownWithConfiguredDefaults() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "success": true,
                  "data": {
                    "markdown": "# Title\\nBody",
                    "metadata": {
                      "title": "Example Page",
                      "sourceURL": "https://example.com/page",
                      "statusCode": 200
                    }
                  }
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com/page")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Example Page"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("https://example.com/page", requestJson.path("url").asText());
        assertEquals("markdown", requestJson.path("formats").get(0).asText());
        assertTrue(requestJson.path("onlyMainContent").asBoolean());
        assertEquals(60000, requestJson.path("maxAge").asInt());
        assertEquals("Bearer fc-test-key", request.header("Authorization"));
    }

    @Test
    void shouldSupportLinksFormatOverride() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "success": true,
                  "data": {
                    "links": [
                      "https://example.com/a",
                      "https://example.com/b"
                    ],
                    "metadata": {
                      "sourceURL": "https://example.com"
                    }
                  }
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of(
                "url", "https://example.com",
                "format", "links",
                "only_main_content", false,
                "timeout_ms", 45000)).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("https://example.com/a"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("links", requestJson.path("formats").get(0).asText());
        assertFalse(requestJson.path("onlyMainContent").asBoolean());
        assertEquals(45000, requestJson.path("timeout").asInt());
    }

    @Test
    void shouldRetryOnRateLimitAndThenSucceed() {
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");
        provider.enqueueResponse(200, """
                {
                  "success": true,
                  "data": {
                    "summary": "Recovered summary",
                    "metadata": {
                      "sourceURL": "https://example.com/recovered"
                    }
                  }
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com", "format", "summary")).join();

        assertTrue(result.isSuccess());
        assertEquals(2, provider.getCapturedRequests().size());
    }

    @Test
    void shouldFailWhenBillingIsRequired() {
        provider.enqueueResponse(402, "{\"error\":\"payment required\"}");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com")).join();

        assertFalse(result.isSuccess());
        assertEquals("Firecrawl credits exhausted or billing is required", result.getError());
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class TestableFirecrawlScrapeToolProvider extends FirecrawlScrapeToolProvider {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private TestableFirecrawlScrapeToolProvider(FirecrawlPluginConfigService configService) {
            super(configService);
        }

        @Override
        protected Response executeRequest(Request request) {
            capturedRequests.add(request);
            PlannedResponse plannedResponse = plannedResponses.remove();
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(plannedResponse.code())
                    .message("mock")
                    .body(ResponseBody.create(plannedResponse.body(), APPLICATION_JSON))
                    .build();
        }

        @Override
        protected void sleepBeforeRetry(long backoffMs) {
            // No-op for tests.
        }

        private void enqueueResponse(int code, String body) {
            plannedResponses.add(new PlannedResponse(code, body));
        }

        private List<Request> getCapturedRequests() {
            return capturedRequests;
        }
    }

    private record PlannedResponse(int code, String body) {
    }
}
