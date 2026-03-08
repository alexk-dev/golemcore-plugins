package me.golemcore.plugins.golemcore.browserless;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.Attachment;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserlessSmartScrapeToolProviderTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private BrowserlessPluginConfigService configService;
    private TestableBrowserlessSmartScrapeToolProvider provider;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        configService = mock(BrowserlessPluginConfigService.class);
        objectMapper = new ObjectMapper();
        BrowserlessPluginConfig config = BrowserlessPluginConfig.builder()
                .enabled(true)
                .apiKey("bl-test-key")
                .baseUrl("https://production-sfo.browserless.io")
                .defaultFormat("markdown")
                .timeoutMs(30000)
                .bestAttempt(false)
                .gotoWaitUntil("networkidle2")
                .gotoTimeoutMs(30000)
                .build();
        when(configService.getConfig()).thenReturn(config);
        provider = new TestableBrowserlessSmartScrapeToolProvider(configService);
    }

    @Test
    void shouldScrapeRenderedMarkdownWithConfiguredDefaults() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "ok": true,
                  "statusCode": 200,
                  "contentType": "text/markdown",
                  "strategy": "content",
                  "attempted": [
                    "/content?timeout=30000"
                  ],
                  "content": "# Title\\nBody"
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com/page")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Browserless smart scrape"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("https://example.com/page", requestJson.path("url").asText());
        assertEquals("markdown", requestJson.path("formats").get(0).asText());
        assertFalse(requestJson.path("bestAttempt").asBoolean());
        assertEquals("networkidle2", requestJson.path("gotoOptions").path("waitUntil").asText());
        assertEquals("Bearer bl-test-key", request.header("Authorization"));
        assertEquals("https://production-sfo.browserless.io/smart-scrape?timeout=30000", request.url().toString());
    }

    @Test
    void shouldSupportLinksFormatAndWaitOptions() throws Exception {
        provider.enqueueResponse(200, """
                {
                  "ok": true,
                  "statusCode": 200,
                  "contentType": "application/json",
                  "strategy": "unblock",
                  "attempted": [
                    "/unblock?timeout=45000",
                    "/content?timeout=45000"
                  ],
                  "content": [
                    "https://example.com/a",
                    "https://example.com/b"
                  ]
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider.execute(Map.of(
                "url", "https://example.com",
                "format", "links",
                "timeout_ms", 45000,
                "best_attempt", true,
                "goto_wait_until", "domcontentloaded",
                "goto_timeout_ms", 20000,
                "wait_for_timeout_ms", 1500,
                "wait_for_selector", ".article",
                "wait_for_selector_timeout_ms", 8000,
                "wait_for_selector_visible", true)).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("https://example.com/a"));
        Request request = provider.getCapturedRequests().getFirst();
        JsonNode requestJson = objectMapper.readTree(readRequestBody(request));
        assertEquals("links", requestJson.path("formats").get(0).asText());
        assertTrue(requestJson.path("bestAttempt").asBoolean());
        assertEquals("domcontentloaded", requestJson.path("gotoOptions").path("waitUntil").asText());
        assertEquals(20000, requestJson.path("gotoOptions").path("timeout").asInt());
        assertEquals(1500, requestJson.path("waitForTimeout").asInt());
        assertEquals(".article", requestJson.path("waitForSelector").path("selector").asText());
        assertEquals(8000, requestJson.path("waitForSelector").path("timeout").asInt());
        assertTrue(requestJson.path("waitForSelector").path("visible").asBoolean());
    }

    @Test
    void shouldRetryOnRateLimitAndThenSucceed() {
        provider.enqueueResponse(429, "{\"message\":\"rate limit\"}");
        provider.enqueueResponse(200, """
                {
                  "ok": true,
                  "statusCode": 200,
                  "contentType": "text/html",
                  "strategy": "content",
                  "attempted": [
                    "/content?timeout=30000"
                  ],
                  "content": "<html><body>Recovered</body></html>"
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com", "format", "html")).join();

        assertTrue(result.isSuccess());
        assertEquals(2, provider.getCapturedRequests().size());
    }

    @Test
    void shouldReturnAttachmentForPdfFormat() {
        provider.enqueueResponse(200, """
                {
                  "ok": true,
                  "statusCode": 200,
                  "contentType": "application/pdf",
                  "strategy": "content",
                  "attempted": [
                    "/pdf?timeout=30000"
                  ],
                  "content": "JVBERi0xLjQ="
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com/report", "format", "pdf")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("browserless-capture.pdf"));
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("browserless-capture.pdf", data.get("filename"));
        Object attachmentObj = data.get("attachment");
        assertInstanceOf(Attachment.class, attachmentObj);
        Attachment attachment = (Attachment) attachmentObj;
        assertEquals(Attachment.Type.DOCUMENT, attachment.getType());
        assertEquals("application/pdf", attachment.getMimeType());
    }

    @Test
    void shouldReturnAttachmentForScreenshotFormat() {
        provider.enqueueResponse(200, """
                {
                  "ok": true,
                  "statusCode": 200,
                  "contentType": "image/png",
                  "strategy": "content",
                  "attempted": [
                    "/screenshot?timeout=30000"
                  ],
                  "content": "iVBORw0KGgo="
                }
                """);

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com/shot", "format", "screenshot")).join();

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("browserless-screenshot.png", data.get("filename"));
        Attachment attachment = (Attachment) data.get("attachment");
        assertEquals(Attachment.Type.IMAGE, attachment.getType());
        assertEquals("image/png", attachment.getMimeType());
    }

    @Test
    void shouldFailWhenAuthenticationFails() {
        provider.enqueueResponse(401, "{\"message\":\"unauthorized\"}");

        me.golemcore.plugin.api.extension.model.ToolResult result = provider
                .execute(Map.of("url", "https://example.com")).join();

        assertFalse(result.isSuccess());
        assertEquals("Browserless authentication failed", result.getError());
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class TestableBrowserlessSmartScrapeToolProvider extends BrowserlessSmartScrapeToolProvider {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private TestableBrowserlessSmartScrapeToolProvider(BrowserlessPluginConfigService configService) {
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
