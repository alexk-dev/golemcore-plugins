package me.golemcore.plugins.golemcore.tavilysearch;

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

class TavilySearchToolProviderTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private TavilySearchPluginConfigService configService;
    private TestableTavilySearchToolProvider provider;
    private ObjectMapper objectMapper;
    private TavilySearchPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(TavilySearchPluginConfigService.class);
        objectMapper = new ObjectMapper();
        config = TavilySearchPluginConfig.builder()
                .enabled(true)
                .apiKey("tvly-test-key")
                .defaultMaxResults(5)
                .defaultTopic("general")
                .defaultSearchDepth("basic")
                .includeAnswer(true)
                .includeRawContent(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
        provider = new TestableTavilySearchToolProvider(configService);
    }

    @Test
    void shouldUseConfiguredDefaultsWhenExecutingSearch() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "answer": "OpenAI released a new update.",
                  "response_time": 0.42,
                  "results": [
                    {
                      "title": "OpenAI news",
                      "url": "https://example.com/openai",
                      "content": "Short summary",
                      "score": 0.98
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("query", "openai update"))
                .join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("OpenAI released a new update."));
        assertTrue(result.getOutput().contains("OpenAI news"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("openai update", requestJson.path("query").asText());
        assertEquals(5, requestJson.path("max_results").asInt());
        assertEquals("general", requestJson.path("topic").asText());
        assertEquals("basic", requestJson.path("search_depth").asText());
        assertTrue(requestJson.path("include_answer").asBoolean());
        assertFalse(requestJson.path("include_raw_content").asBoolean());
        assertEquals("Bearer tvly-test-key", request.header("Authorization"));
    }

    @Test
    void shouldApplyOverridesAndDomainFiltersWhenProvided() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "results": [
                    {
                      "title": "Result",
                      "url": "https://example.com/result",
                      "content": "Body",
                      "raw_content": "Raw body"
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of(
                "query", "ai security",
                "max_results", 3,
                "topic", "news",
                "search_depth", "advanced",
                "include_answer", false,
                "include_raw_content", true,
                "include_domains", List.of("example.com", "openai.com"),
                "exclude_domains", "reddit.com, x.com")).join();

        assertTrue(result.isSuccess());
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals(3, requestJson.path("max_results").asInt());
        assertEquals("news", requestJson.path("topic").asText());
        assertEquals("advanced", requestJson.path("search_depth").asText());
        assertFalse(requestJson.path("include_answer").asBoolean());
        assertTrue(requestJson.path("include_raw_content").asBoolean());
        assertEquals(2, requestJson.path("include_domains").size());
        assertEquals(2, requestJson.path("exclude_domains").size());
    }

    @Test
    void shouldRetryOnRateLimitAndEventuallySucceed() {
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");
        provider.enqueueResponse(200, """
                {
                  "results": [
                    {
                      "title": "Recovered",
                      "url": "https://example.com/recovered",
                      "content": "Recovered content"
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("query", "rate limit test"))
                .join();

        assertTrue(result.isSuccess());
        assertEquals(2, provider.getCapturedRequests().size());
    }

    @Test
    void shouldFailWhenAuthenticationIsRejected() {
        provider.enqueueResponse(401, "{\"error\":\"unauthorized\"}");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("query", "auth failure"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Tavily Search authentication failed", result.getError());
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class TestableTavilySearchToolProvider extends TavilySearchToolProvider {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private TestableTavilySearchToolProvider(TavilySearchPluginConfigService configService) {
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
