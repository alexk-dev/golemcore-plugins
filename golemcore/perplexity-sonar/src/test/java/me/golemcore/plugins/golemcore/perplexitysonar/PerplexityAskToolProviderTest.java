package me.golemcore.plugins.golemcore.perplexitysonar;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerplexityAskToolProviderTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private PerplexitySonarPluginConfigService configService;
    private MockPerplexityAskToolProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        configService = mock(PerplexitySonarPluginConfigService.class);
        objectMapper = new ObjectMapper();
        PerplexitySonarPluginConfig config = PerplexitySonarPluginConfig.builder()
                .enabled(true)
                .apiKey("pplx-test-key")
                .defaultModel("sonar")
                .defaultSearchMode("web")
                .returnRelatedQuestions(false)
                .returnImages(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
        provider = new MockPerplexityAskToolProvider(configService);
    }

    @Test
    void shouldSendSynchronousCompletionWithConfiguredDefaults() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Grounded answer"
                      }
                    }
                  ],
                  "search_results": [
                    {
                      "title": "Source 1",
                      "url": "https://example.com/source",
                      "date": "2026-03-07"
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("question", "What happened today?")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Grounded answer"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("sonar", requestJson.path("model").asText());
        assertFalse(requestJson.path("stream").asBoolean());
        assertEquals("web", requestJson.path("search_mode").asText());
        assertEquals("What happened today?",
                requestJson.path("messages").get(0).path("content").asText());
        assertEquals("Bearer pplx-test-key", request.header("Authorization"));
    }

    @Test
    void shouldApplyModelAndSearchOverrides() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Academic answer"
                      }
                    }
                  ],
                  "related_questions": [
                    "Follow-up?"
                  ]
                }
                """);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("question", "Explain the paper");
        requestPayload.put("model", "sonar-pro");
        requestPayload.put("system_prompt", "Be concise");
        requestPayload.put("search_mode", "academic");
        requestPayload.put("search_domain_filter", List.of("arxiv.org"));
        requestPayload.put("search_language_filter", "en, de");
        requestPayload.put("search_recency_filter", "month");
        requestPayload.put("max_tokens", 512);
        requestPayload.put("temperature", 0.2);
        requestPayload.put("return_related_questions", true);
        requestPayload.put("return_images", true);
        requestPayload.put("reasoning_effort", "low");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(requestPayload).join();

        assertTrue(result.isSuccess());
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("sonar-pro", requestJson.path("model").asText());
        assertEquals("academic", requestJson.path("search_mode").asText());
        assertEquals(2, requestJson.path("messages").size());
        assertEquals("system", requestJson.path("messages").get(0).path("role").asText());
        assertEquals(1, requestJson.path("search_domain_filter").size());
        assertEquals(2, requestJson.path("search_language_filter").size());
        assertEquals("month", requestJson.path("search_recency_filter").asText());
        assertEquals(512, requestJson.path("max_tokens").asInt());
        assertTrue(requestJson.path("return_related_questions").asBoolean());
        assertTrue(requestJson.path("return_images").asBoolean());
        assertEquals("low", requestJson.path("reasoning_effort").asText());
        assertTrue(result.getOutput().contains("Academic answer\n\nRelated questions:"));
    }

    @Test
    void shouldRetryOnRateLimitAndThenSucceed() {
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");
        provider.enqueueResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Recovered answer"
                      }
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "retry please"))
                .join();

        assertTrue(result.isSuccess());
        assertEquals(2, provider.getCapturedRequests().size());
    }

    @Test
    void shouldRetryOnIoFailureAndThenSucceed() {
        provider.enqueueIoFailure("timeout");
        provider.enqueueResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Recovered after timeout"
                      }
                    }
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "retry io"))
                .join();

        assertTrue(result.isSuccess());
        assertEquals(2, provider.getCapturedRequests().size());
    }

    @Test
    void shouldFailWhenAuthenticationIsRejected() {
        provider.enqueueResponse(401, "{\"error\":\"unauthorized\"}");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "auth failure"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Perplexity authentication failed", result.getError());
    }

    @Test
    void shouldFailWhenQuestionIsMissing() {
        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "   ")).join();

        assertFalse(result.isSuccess());
        assertEquals("Question is required", result.getError());
        assertTrue(provider.getCapturedRequests().isEmpty());
    }

    @Test
    void shouldFailWhenPluginIsDisabled() {
        PerplexitySonarPluginConfig disabledConfig = PerplexitySonarPluginConfig.builder()
                .enabled(false)
                .apiKey("pplx-test-key")
                .defaultModel("sonar")
                .defaultSearchMode("web")
                .returnRelatedQuestions(false)
                .returnImages(false)
                .build();
        when(configService.getConfig()).thenReturn(disabledConfig);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "disabled"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Perplexity Sonar is disabled or API key is missing", result.getError());
        assertTrue(provider.getCapturedRequests().isEmpty());
    }

    @Test
    void shouldNormalizeInvalidParametersAndParseStringNumbers() throws Exception {
        PerplexitySonarPluginConfig configWithCustomDefaults = PerplexitySonarPluginConfig.builder()
                .enabled(true)
                .apiKey("pplx-test-key")
                .defaultModel("sonar-pro")
                .defaultSearchMode("academic")
                .returnRelatedQuestions(false)
                .returnImages(false)
                .build();
        when(configService.getConfig()).thenReturn(configWithCustomDefaults);
        provider.enqueueResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Normalized answer"
                      }
                    }
                  ]
                }
                """);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("question", "Normalize this");
        requestPayload.put("model", "not-supported");
        requestPayload.put("search_mode", "not-supported");
        requestPayload.put("search_domain_filter", " example.com,\nfoo.com , example.com ");
        requestPayload.put("search_language_filter", List.of(" en ", "de", "en"));
        requestPayload.put("search_recency_filter", "decade");
        requestPayload.put("max_tokens", "256");
        requestPayload.put("temperature", "0.75");
        requestPayload.put("reasoning_effort", "extreme");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(requestPayload).join();

        assertTrue(result.isSuccess());
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("sonar-pro", requestJson.path("model").asText());
        assertEquals("academic", requestJson.path("search_mode").asText());
        assertEquals(256, requestJson.path("max_tokens").asInt());
        assertEquals(0.75, requestJson.path("temperature").asDouble());
        assertFalse(requestJson.has("search_recency_filter"));
        assertFalse(requestJson.has("reasoning_effort"));
        assertEquals(List.of("example.com", "foo.com"),
                objectMapper.convertValue(requestJson.path("search_domain_filter"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
        assertEquals(List.of("en", "de"),
                objectMapper.convertValue(requestJson.path("search_language_filter"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
    }

    @Test
    void shouldFailAfterRetryExhaustedOnRateLimit() {
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");
        provider.enqueueResponse(429, "{\"error\":\"rate limit\"}");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "retry all"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Perplexity rate limit exceeded", result.getError());
        assertEquals(3, provider.getCapturedRequests().size());
    }

    @Test
    void shouldFailAfterRetryExhaustedOnIoFailure() {
        provider.enqueueIoFailure("timeout");
        provider.enqueueIoFailure("timeout");
        provider.enqueueIoFailure("timeout");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "retry io all"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Perplexity request failed: timeout", result.getError());
        assertEquals(3, provider.getCapturedRequests().size());
    }

    @Test
    void shouldFailWhenApiReturnsEmptyAnswer() {
        provider.enqueueResponse(200, """
                {
                  "choices": []
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "empty"))
                .join();

        assertFalse(result.isSuccess());
        assertEquals("Perplexity returned an empty answer", result.getError());
    }

    @Test
    void shouldFailGracefullyWhenApiReturnsMalformedJson() {
        provider.enqueueResponse(200, "{not-json");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of("question", "bad json"))
                .join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().startsWith("Perplexity request failed:"));
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class MockPerplexityAskToolProvider extends PerplexityAskToolProvider {

        private final Queue<PlannedCall> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private MockPerplexityAskToolProvider(PerplexitySonarPluginConfigService configService) {
            super(configService);
        }

        @Override
        protected Response executeRequest(Request request) throws IOException {
            capturedRequests.add(request);
            PlannedCall plannedResponse = plannedResponses.remove();
            if (plannedResponse.failure() != null) {
                throw plannedResponse.failure();
            }
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
            plannedResponses.add(new PlannedCall(code, body, null));
        }

        private void enqueueIoFailure(String message) {
            plannedResponses.add(new PlannedCall(0, null, new IOException(message)));
        }

        private List<Request> getCapturedRequests() {
            return capturedRequests;
        }
    }

    private record PlannedCall(int code, String body, IOException failure) {
    }
}
