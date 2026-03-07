package me.golemcore.plugins.golemcore.lightrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.spi.RagProvider;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class LightRagProvider implements RagProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final LightRagPluginConfigService configService;
    private final ObjectMapper objectMapper;

    public LightRagProvider(LightRagPluginConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderId() {
        return LightRagPluginConfigService.PLUGIN_ID;
    }

    @Override
    public boolean isAvailable() {
        LightRagPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && config.getUrl() != null && !config.getUrl().isBlank();
    }

    @Override
    public CompletableFuture<String> query(String query) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture("");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                LightRagPluginConfig config = configService.getConfig();
                String url = stripTrailingSlash(config.getUrl()) + "/query";
                String body = objectMapper.writeValueAsString(new QueryRequest(query, config.getQueryMode()));
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body, JSON));
                addApiKeyHeader(requestBuilder, config);
                try (Response response = client(config).newCall(requestBuilder.build()).execute()) {
                    ResponseBody responseBody = response.body();
                    if (!response.isSuccessful() || responseBody == null) {
                        return "";
                    }
                    return parseQueryResponse(responseBody.string());
                }
            } catch (Exception e) {
                return "";
            }
        });
    }

    @Override
    public CompletableFuture<Void> index(String content) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                if (content == null || content.isBlank()) {
                    return;
                }
                LightRagPluginConfig config = configService.getConfig();
                String url = stripTrailingSlash(config.getUrl()) + "/documents/text";
                String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        .withZone(ZoneOffset.UTC)
                        .format(Instant.now());
                String body = objectMapper.writeValueAsString(new IndexRequest(content, "conv_" + timestamp + ".txt"));
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body, JSON));
                addApiKeyHeader(requestBuilder, config);
                try (Response ignored = client(config).newCall(requestBuilder.build()).execute()) {
                    // best effort, failures are ignored by host systems
                }
            } catch (IOException ignored) {
                // best effort, failures are ignored by host systems
            }
        });
    }

    @Override
    public int getIndexMinLength() {
        return configService.getConfig().getIndexMinLength();
    }

    private OkHttpClient client(LightRagPluginConfig config) {
        int timeoutSeconds = config.getTimeoutSeconds();
        return new OkHttpClient.Builder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    private void addApiKeyHeader(Request.Builder builder, LightRagPluginConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }
    }

    private String parseQueryResponse(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.has("response")) {
                return node.get("response").asText("");
            }
            return responseBody.trim();
        } catch (JsonProcessingException e) {
            return responseBody.trim();
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record QueryRequest(String query, String mode) {
    }

    private record IndexRequest(String text, String file_source) {
    }
}
