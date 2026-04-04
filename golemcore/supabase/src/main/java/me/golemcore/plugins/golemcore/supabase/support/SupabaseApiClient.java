package me.golemcore.plugins.golemcore.supabase.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.supabase.SupabasePluginConfig;
import me.golemcore.plugins.golemcore.supabase.SupabasePluginConfigService;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SupabaseApiClient {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SupabasePluginConfigService configService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SupabaseApiClient(SupabasePluginConfigService configService) {
        this.configService = configService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> selectRows(
            String table,
            String schema,
            String select,
            Integer limit,
            Integer offset,
            String orderBy,
            Optional<Boolean> ascending,
            Map<String, String> filters) {
        HttpUrl.Builder builder = createTableUrlBuilder(table);
        builder.addQueryParameter("select", select);
        if (limit != null) {
            builder.addQueryParameter("limit", Integer.toString(limit));
        }
        if (offset != null) {
            builder.addQueryParameter("offset", Integer.toString(offset));
        }
        if (hasText(orderBy)) {
            String direction = ascending.orElse(Boolean.TRUE) ? "asc" : "desc";
            builder.addQueryParameter("order", orderBy + "." + direction);
        }
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            builder.addQueryParameter(filter.getKey(), filter.getValue());
        }

        Request request = authorizedRequest(builder.build())
                .header("Accept-Profile", schema)
                .get()
                .build();
        return executeRows(request);
    }

    public List<Map<String, Object>> insertRow(String table, String schema, Map<String, Object> values) {
        Request request = authorizedRequest(createTableUrlBuilder(table).build())
                .header("Content-Profile", schema)
                .header("Prefer", "return=representation")
                .post(buildBody(values))
                .build();
        return executeRows(request);
    }

    public List<Map<String, Object>> updateRows(
            String table,
            String schema,
            Map<String, String> filters,
            Map<String, Object> values) {
        HttpUrl.Builder builder = createTableUrlBuilder(table);
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            builder.addQueryParameter(filter.getKey(), filter.getValue());
        }
        Request request = authorizedRequest(builder.build())
                .header("Content-Profile", schema)
                .header("Prefer", "return=representation")
                .patch(buildBody(values))
                .build();
        return executeRows(request);
    }

    public List<Map<String, Object>> deleteRows(String table, String schema, Map<String, String> filters) {
        HttpUrl.Builder builder = createTableUrlBuilder(table);
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            builder.addQueryParameter(filter.getKey(), filter.getValue());
        }
        Request request = authorizedRequest(builder.build())
                .header("Accept-Profile", schema)
                .header("Prefer", "return=representation")
                .delete()
                .build();
        return executeRows(request);
    }

    protected Response executeRequest(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }

    private Request.Builder authorizedRequest(HttpUrl url) {
        SupabasePluginConfig config = configService.getConfig();
        return new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("apikey", config.getApiKey())
                .header("Authorization", "Bearer " + config.getApiKey());
    }

    private RequestBody buildBody(Map<String, Object> values) {
        try {
            return RequestBody.create(objectMapper.writeValueAsString(values), APPLICATION_JSON);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize Supabase request body", ex);
        }
    }

    private List<Map<String, Object>> executeRows(Request request) {
        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new SupabaseApiException(response.code(), extractErrorMessage(response.code(), responseBody));
            }
            if (responseBody.isBlank()) {
                return List.of();
            }
            return parseRows(objectMapper.readTree(responseBody));
        } catch (SupabaseApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new SupabaseTransportException("Supabase transport failed: " + ex.getMessage(), ex);
        }
    }

    private List<Map<String, Object>> parseRows(JsonNode root) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode rowNode : root) {
                rows.add(objectMapper.convertValue(rowNode, MAP_TYPE));
            }
            return rows;
        }
        if (root.isObject()) {
            rows.add(objectMapper.convertValue(root, MAP_TYPE));
        }
        return rows;
    }

    private String extractErrorMessage(int statusCode, String responseBody) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                String message = readText(root.get("message"));
                String details = readText(root.get("details"));
                String hint = readText(root.get("hint"));
                StringBuilder error = new StringBuilder();
                if (hasText(message)) {
                    error.append(message);
                }
                if (hasText(details)) {
                    if (error.length() > 0) {
                        error.append(" | ");
                    }
                    error.append(details);
                }
                if (hasText(hint)) {
                    if (error.length() > 0) {
                        error.append(" | ");
                    }
                    error.append("hint: ").append(hint);
                }
                if (error.length() > 0) {
                    return error.toString();
                }
            } catch (IOException ignored) {
                return "HTTP " + statusCode + ": " + responseBody;
            }
            return "HTTP " + statusCode + ": " + responseBody;
        }
        return "HTTP " + statusCode;
    }

    private HttpUrl.Builder createTableUrlBuilder(String table) {
        return createBaseUrlBuilder().addPathSegment(table);
    }

    private HttpUrl.Builder createBaseUrlBuilder() {
        SupabasePluginConfig config = configService.getConfig();
        URI uri = URI.create(config.getProjectUrl());
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Invalid Supabase project URL: " + config.getProjectUrl());
        }
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme(uri.getScheme())
                .host(uri.getHost());
        if (uri.getPort() != -1) {
            builder.port(uri.getPort());
        }
        String normalizedPath = normalizePath(uri.getPath());
        addPathSegments(builder, normalizedPath);
        if (!normalizedPath.endsWith("/rest/v1") && !"rest/v1".equals(normalizedPath)) {
            builder.addPathSegment("rest");
            builder.addPathSegment("v1");
        }
        return builder;
    }

    private void addPathSegments(HttpUrl.Builder builder, String path) {
        if (!hasText(path)) {
            return;
        }
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                builder.addPathSegment(segment);
            }
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String readText(JsonNode node) {
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
