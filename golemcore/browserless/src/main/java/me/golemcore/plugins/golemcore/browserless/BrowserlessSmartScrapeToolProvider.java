package me.golemcore.plugins.golemcore.browserless;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class BrowserlessSmartScrapeToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_URL = "url";
    private static final String PARAM_FORMAT = "format";
    private static final String PARAM_TIMEOUT_MS = "timeout_ms";
    private static final String PARAM_BEST_ATTEMPT = "best_attempt";
    private static final String PARAM_GOTO_WAIT_UNTIL = "goto_wait_until";
    private static final String PARAM_GOTO_TIMEOUT_MS = "goto_timeout_ms";
    private static final String PARAM_WAIT_FOR_TIMEOUT_MS = "wait_for_timeout_ms";
    private static final String PARAM_WAIT_FOR_SELECTOR = "wait_for_selector";
    private static final String PARAM_WAIT_FOR_SELECTOR_TIMEOUT_MS = "wait_for_selector_timeout_ms";
    private static final String PARAM_WAIT_FOR_SELECTOR_VISIBLE = "wait_for_selector_visible";
    private static final int DEFAULT_WAIT_FOR_SELECTOR_TIMEOUT_MS = 10_000;
    private static final int MAX_TIMEOUT_MS = 300_000;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final int MAX_OUTPUT_CHARS = 12_000;
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("markdown", "html", "links", "pdf", "screenshot");
    private static final Set<String> BINARY_FORMATS = Set.of("pdf", "screenshot");
    private static final Set<String> SUPPORTED_WAIT_UNTIL = Set.of(
            "load",
            "domcontentloaded",
            "networkidle0",
            "networkidle2");

    private final BrowserlessPluginConfigService configService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isEnabled() {
        BrowserlessPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && hasText(config.getApiKey());
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("browserless_smart_scrape")
                .description("Render and scrape a webpage through Browserless smart-scrape.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_URL, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Absolute HTTP(S) URL to scrape."),
                                PARAM_FORMAT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Desired output format: markdown, html, links, pdf, or screenshot."),
                                PARAM_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Global Browserless request timeout in milliseconds."),
                                PARAM_BEST_ATTEMPT, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Continue with partial output if waits or navigation fail."),
                                PARAM_GOTO_WAIT_UNTIL, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Navigation readiness: load, domcontentloaded, networkidle0, or networkidle2."),
                                PARAM_GOTO_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Navigation timeout in milliseconds."),
                                PARAM_WAIT_FOR_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Fixed delay in milliseconds before returning content."),
                                PARAM_WAIT_FOR_SELECTOR, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional CSS selector to wait for before scraping."),
                                PARAM_WAIT_FOR_SELECTOR_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Selector wait timeout in milliseconds."),
                                PARAM_WAIT_FOR_SELECTOR_VISIBLE, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Require the selector to be visible before continuing.")),
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
            throw new IllegalStateException("Browserless retry interrupted", ex);
        }
    }

    private ToolResult executeScrape(Map<String, Object> parameters) {
        String url = readString(parameters.get(PARAM_URL));
        if (!hasText(url)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Scrape URL is required");
        }
        if (!isEnabled()) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Browserless is disabled or API key is missing");
        }

        BrowserlessPluginConfig config = configService.getConfig();
        String format = normalizeFormat(readString(parameters.get(PARAM_FORMAT)), config.getDefaultFormat());
        int timeoutMs = readInteger(parameters.get(PARAM_TIMEOUT_MS), config.getTimeoutMs(), 1_000, MAX_TIMEOUT_MS);
        boolean bestAttempt = readBoolean(parameters.get(PARAM_BEST_ATTEMPT), config.getBestAttempt());
        String gotoWaitUntil = normalizeWaitUntil(readString(parameters.get(PARAM_GOTO_WAIT_UNTIL)),
                config.getGotoWaitUntil());
        int gotoTimeoutMs = readInteger(
                parameters.get(PARAM_GOTO_TIMEOUT_MS),
                config.getGotoTimeoutMs(),
                1_000,
                MAX_TIMEOUT_MS);
        int waitForTimeoutMs = readInteger(parameters.get(PARAM_WAIT_FOR_TIMEOUT_MS), 0, 0, MAX_TIMEOUT_MS);
        String waitForSelector = readString(parameters.get(PARAM_WAIT_FOR_SELECTOR));
        int waitForSelectorTimeoutMs = readInteger(
                parameters.get(PARAM_WAIT_FOR_SELECTOR_TIMEOUT_MS),
                DEFAULT_WAIT_FOR_SELECTOR_TIMEOUT_MS,
                0,
                MAX_TIMEOUT_MS);
        boolean waitForSelectorVisible = readBoolean(parameters.get(PARAM_WAIT_FOR_SELECTOR_VISIBLE), true);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                BrowserlessScrapeResult result = scrape(
                        url,
                        format,
                        timeoutMs,
                        bestAttempt,
                        gotoWaitUntil,
                        gotoTimeoutMs,
                        waitForTimeoutMs,
                        waitForSelector,
                        waitForSelectorTimeoutMs,
                        waitForSelectorVisible);
                return buildSuccessResult(url, format, result);
            } catch (BrowserlessRequestException ex) {
                if (shouldRetry(ex.statusCode()) && attempt < MAX_RETRIES - 1) {
                    sleepBeforeRetry((long) (INITIAL_BACKOFF_MS * Math.pow(2, attempt)));
                    continue;
                }
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, mapErrorMessage(ex.statusCode(), ex));
            } catch (Exception ex) { // NOSONAR - tool I/O should degrade gracefully
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                        "Browserless request failed: " + ex.getMessage());
            }
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Browserless request failed");
    }

    private BrowserlessScrapeResult scrape(
            String url,
            String format,
            int timeoutMs,
            boolean bestAttempt,
            String gotoWaitUntil,
            int gotoTimeoutMs,
            int waitForTimeoutMs,
            String waitForSelector,
            int waitForSelectorTimeoutMs,
            boolean waitForSelectorVisible) throws IOException {
        BrowserlessPluginConfig config = configService.getConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put(PARAM_URL, url);
        requestBody.put("formats", List.of(format));
        requestBody.put("bestAttempt", bestAttempt);
        requestBody.put("gotoOptions", Map.of(
                "waitUntil", gotoWaitUntil,
                "timeout", gotoTimeoutMs));
        if (waitForTimeoutMs > 0) {
            requestBody.put("waitForTimeout", waitForTimeoutMs);
        }
        if (hasText(waitForSelector)) {
            Map<String, Object> waitForSelectorBody = new LinkedHashMap<>();
            waitForSelectorBody.put("selector", waitForSelector);
            waitForSelectorBody.put("timeout", waitForSelectorTimeoutMs);
            waitForSelectorBody.put("visible", waitForSelectorVisible);
            requestBody.put("waitForSelector", waitForSelectorBody);
        }

        Request request = new Request.Builder()
                .url(buildRequestUrl(config.getBaseUrl(), timeoutMs))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), APPLICATION_JSON))
                .build();

        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new BrowserlessRequestException(response.code(), readErrorMessage(responseBody, response.code()));
            }
            if (!hasText(responseBody)) {
                throw new BrowserlessRequestException(response.code(), "Empty response body");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("ok") && !root.path("ok").asBoolean(true)) {
                throw new BrowserlessRequestException(
                        response.code(),
                        firstText(readNodeText(root, "message"), "Browserless returned unsuccessful response"));
            }
            return parseScrapeResult(root, format, url);
        }
    }

    private String buildRequestUrl(String baseUrl, int timeoutMs) {
        return baseUrl + "/smart-scrape?timeout=" + timeoutMs;
    }

    private BrowserlessScrapeResult parseScrapeResult(JsonNode root, String format, String fallbackUrl) {
        JsonNode contentNode = root.path("content");
        Object content = contentNode.isMissingNode() || contentNode.isNull()
                ? ("links".equals(format) ? List.of() : "")
                : objectMapper.convertValue(contentNode, Object.class);
        String renderedContent = renderContent(contentNode);
        return new BrowserlessScrapeResult(
                firstText(readNodeText(root, PARAM_URL), fallbackUrl),
                format,
                root.path("statusCode").isNumber() ? root.path("statusCode").asInt() : null,
                readNodeText(root, "contentType"),
                readNodeText(root, "strategy"),
                readStringList(root.path("attempted")),
                readNodeText(root, "message"),
                content,
                renderedContent);
    }

    private ToolResult buildSuccessResult(String url, String format, BrowserlessScrapeResult result) {
        if (BINARY_FORMATS.contains(format)) {
            return buildBinarySuccessResult(url, format, result);
        }

        StringBuilder output = new StringBuilder();
        output.append("Browserless smart scrape for ")
                .append(result.url() != null ? result.url() : url)
                .append(" (format: ")
                .append(format)
                .append(")\n");
        if (result.statusCode() != null) {
            output.append("HTTP Status: ").append(result.statusCode()).append('\n');
        }
        if (hasText(result.contentType())) {
            output.append("Content-Type: ").append(result.contentType()).append('\n');
        }
        if (hasText(result.strategy())) {
            output.append("Strategy: ").append(result.strategy()).append('\n');
        }
        if (!result.attempted().isEmpty()) {
            output.append("Attempts: ").append(String.join(", ", result.attempted())).append('\n');
        }
        if (hasText(result.message())) {
            output.append("Message: ").append(result.message()).append('\n');
        }
        if (hasText(result.renderedContent())) {
            output.append('\n').append(truncate(result.renderedContent(), MAX_OUTPUT_CHARS));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", result.url() != null ? result.url() : url);
        data.put("format", result.format());
        data.put("statusCode", result.statusCode());
        data.put("contentType", firstText(result.contentType(), ""));
        data.put("strategy", firstText(result.strategy(), ""));
        data.put("attempted", result.attempted());
        data.put("message", firstText(result.message(), ""));
        data.put("content", result.content());
        return ToolResult.success(output.toString(), data);
    }

    private ToolResult buildBinarySuccessResult(String url, String format, BrowserlessScrapeResult result) {
        String base64Payload = result.renderedContent();
        if (!hasText(base64Payload)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Browserless did not return a binary payload for format: " + format);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException ex) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Browserless returned invalid base64 payload for format: " + format);
        }

        String mimeType = resolveBinaryMimeType(format, result.contentType());
        String filename = resolveBinaryFilename(format, mimeType);
        Attachment.Type attachmentType = mimeType.startsWith("image/")
                ? Attachment.Type.IMAGE
                : Attachment.Type.DOCUMENT;
        Attachment attachment = Attachment.builder()
                .type(attachmentType)
                .data(bytes)
                .filename(filename)
                .mimeType(mimeType)
                .caption("Browserless " + format + " capture for " + (result.url() != null ? result.url() : url))
                .build();

        StringBuilder output = new StringBuilder();
        output.append("Browserless ").append(format).append(" capture for ")
                .append(result.url() != null ? result.url() : url)
                .append('\n');
        if (result.statusCode() != null) {
            output.append("HTTP Status: ").append(result.statusCode()).append('\n');
        }
        if (hasText(result.strategy())) {
            output.append("Strategy: ").append(result.strategy()).append('\n');
        }
        output.append("File: ").append(filename).append('\n');
        output.append("Size: ").append(bytes.length).append(" bytes");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", result.url() != null ? result.url() : url);
        data.put("format", format);
        data.put("statusCode", result.statusCode());
        data.put("contentType", mimeType);
        data.put("strategy", firstText(result.strategy(), ""));
        data.put("attempted", result.attempted());
        data.put("message", firstText(result.message(), ""));
        data.put("filename", filename);
        data.put("attachment", attachment);
        return ToolResult.success(output.toString(), data);
    }

    private String renderContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : contentNode) {
                values.add(item.isTextual() ? item.asText() : item.toPrettyString());
            }
            return String.join("\n", values);
        }
        return contentNode.toPrettyString();
    }

    private String readErrorMessage(String responseBody, int statusCode) {
        if (hasText(responseBody)) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                return firstText(readNodeText(root, "message"), readNodeText(root, "error"), "HTTP " + statusCode);
            } catch (IOException ignored) {
                return responseBody;
            }
        }
        return "HTTP " + statusCode;
    }

    private String resolveBinaryMimeType(String format, String responseContentType) {
        if (hasText(responseContentType)) {
            return responseContentType;
        }
        return switch (format) {
        case "pdf" -> "application/pdf";
        case "screenshot" -> "image/png";
        default -> "application/octet-stream";
        };
    }

    private String resolveBinaryFilename(String format, String mimeType) {
        return switch (format) {
        case "pdf" -> "browserless-capture.pdf";
        case "screenshot" -> mimeType.endsWith("jpeg")
                ? "browserless-screenshot.jpg"
                : mimeType.endsWith("webp")
                        ? "browserless-screenshot.webp"
                        : "browserless-screenshot.png";
        default -> "browserless-download.bin";
        };
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String mapErrorMessage(int statusCode, BrowserlessRequestException ex) {
        return switch (statusCode) {
        case 401, 403 -> "Browserless authentication failed";
        case 408, 504 -> "Browserless request timed out";
        case 429 -> "Browserless rate limit exceeded";
        default -> statusCode >= 500
                ? "Browserless request failed: service unavailable"
                : "Browserless request failed: " + ex.getMessage();
        };
    }

    private String normalizeFormat(String requestedFormat, String fallbackFormat) {
        if (requestedFormat != null && SUPPORTED_FORMATS.contains(requestedFormat)) {
            return requestedFormat;
        }
        if (fallbackFormat != null && SUPPORTED_FORMATS.contains(fallbackFormat)) {
            return fallbackFormat;
        }
        return "markdown";
    }

    private String normalizeWaitUntil(String requestedWaitUntil, String fallbackWaitUntil) {
        if (requestedWaitUntil != null && SUPPORTED_WAIT_UNTIL.contains(requestedWaitUntil)) {
            return requestedWaitUntil;
        }
        if (fallbackWaitUntil != null && SUPPORTED_WAIT_UNTIL.contains(fallbackWaitUntil)) {
            return fallbackWaitUntil;
        }
        return "networkidle2";
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private int readInteger(Object value, int defaultValue, int minValue, int maxValue) {
        Integer candidate = null;
        if (value instanceof Number number) {
            candidate = number.intValue();
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                candidate = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        if (candidate == null) {
            return defaultValue;
        }
        if (candidate < minValue) {
            return minValue;
        }
        return Math.min(candidate, maxValue);
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private String readString(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private String readNodeText(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.path(fieldName).isNull()) {
            return node.path(fieldName).asText();
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 3) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record BrowserlessScrapeResult(
            String url,
            String format,
            Integer statusCode,
            String contentType,
            String strategy,
            List<String> attempted,
            String message,
            Object content,
            String renderedContent) {
    }

    private static final class BrowserlessRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int httpStatusCode;

        private BrowserlessRequestException(int statusCode, String message) {
            super(message);
            this.httpStatusCode = statusCode;
        }

        private int statusCode() {
            return httpStatusCode;
        }
    }
}
