package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PinchTabHttpClient {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private final PinchTabPluginConfigService configService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isEnabled() {
        return Boolean.TRUE.equals(getConfig().getEnabled());
    }

    public PinchTabPluginConfig getConfig() {
        return configService.getConfig();
    }

    public JsonNode getJson(String path, Map<String, Object> query) throws IOException {
        PinchTabResponse response = execute("GET", path, query, null);
        if (response.body().length == 0) {
            return objectMapper.createObjectNode();
        }
        JsonNode root = objectMapper.readTree(response.body());
        validateLogicalSuccess(root, response.statusCode());
        return root;
    }

    public JsonNode postJson(String path, Map<String, Object> body) throws IOException {
        PinchTabResponse response = execute("POST", path, Map.of(), body);
        if (response.body().length == 0) {
            return objectMapper.createObjectNode();
        }
        JsonNode root = objectMapper.readTree(response.body());
        validateLogicalSuccess(root, response.statusCode());
        return root;
    }

    public String getText(String path, Map<String, Object> query) throws IOException {
        PinchTabResponse response = execute("GET", path, query, null);
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    public byte[] getBytes(String path, Map<String, Object> query) throws IOException {
        PinchTabResponse response = execute("GET", path, query, null);
        return response.body();
    }

    public Object toObject(JsonNode node) {
        return node == null ? null : objectMapper.convertValue(node, Object.class);
    }

    public String prettyPrint(JsonNode node) throws IOException {
        return node == null ? "" : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    protected Response executeRequest(OkHttpClient client, Request request) throws IOException {
        return client.newCall(request).execute();
    }

    private PinchTabResponse execute(String method, String path, Map<String, Object> query, Map<String, Object> body)
            throws IOException {
        PinchTabPluginConfig config = getConfig();
        HttpUrl url = buildUrl(config.getBaseUrl(), path, query);
        OkHttpClient requestClient = httpClient.newBuilder()
                .callTimeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .build();
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json");
        if (hasText(config.getApiToken())) {
            requestBuilder.header("Authorization", "Bearer " + config.getApiToken());
        }
        if ("POST".equals(method)) {
            String json = objectMapper.writeValueAsString(body != null ? body : Map.of());
            requestBuilder.post(RequestBody.create(json, APPLICATION_JSON));
        } else {
            requestBuilder.get();
        }

        try (Response response = executeRequest(requestClient, requestBuilder.build());
                ResponseBody responseBody = response.body()) {
            byte[] bytes = responseBody != null ? responseBody.bytes() : new byte[0];
            if (!response.isSuccessful()) {
                throw new PinchTabRequestException(response.code(), extractErrorMessage(bytes, response.code()));
            }
            return new PinchTabResponse(response.code(), bytes);
        }
    }

    private HttpUrl buildUrl(String baseUrl, String path, Map<String, Object> query) {
        HttpUrl httpUrl = HttpUrl.parse(baseUrl + path);
        if (httpUrl == null) {
            throw new IllegalStateException("Invalid PinchTab base URL: " + baseUrl);
        }
        HttpUrl.Builder builder = httpUrl.newBuilder();
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (text.isBlank()) {
                continue;
            }
            builder.addQueryParameter(entry.getKey(), text);
        }
        return builder.build();
    }

    private void validateLogicalSuccess(JsonNode root, int statusCode) throws PinchTabRequestException {
        if (root != null
                && root.isObject()
                && "error".equalsIgnoreCase(root.path("status").asText())) {
            throw new PinchTabRequestException(statusCode,
                    firstText(root.path("error").asText(null),
                            root.path("message").asText(null),
                            "PinchTab returned an error"));
        }
    }

    private String extractErrorMessage(byte[] bytes, int statusCode) {
        if (bytes.length == 0) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode root = objectMapper.readTree(bytes);
            if (root != null && root.isObject()) {
                return firstText(root.path("error").asText(null),
                        root.path("message").asText(null),
                        root.path("status").asText(null),
                        "HTTP " + statusCode);
            }
        } catch (IOException ignored) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return "HTTP " + statusCode;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PinchTabResponse(int statusCode, byte[] body) {
    }
}
