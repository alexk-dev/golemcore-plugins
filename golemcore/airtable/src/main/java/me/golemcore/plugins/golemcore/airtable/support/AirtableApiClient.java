package me.golemcore.plugins.golemcore.airtable.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.airtable.AirtablePluginConfig;
import me.golemcore.plugins.golemcore.airtable.AirtablePluginConfigService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AirtableApiClient {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AirtablePluginConfigService configService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AirtableApiClient(AirtablePluginConfigService configService) {
        this.configService = configService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public AirtableListResponse listRecords(
            String table,
            String view,
            String filterByFormula,
            Integer maxRecords,
            List<String> fieldNames,
            String sortField,
            String sortDirection) {
        HttpUrl.Builder builder = createTableUrlBuilder(table);
        if (hasText(view)) {
            builder.addQueryParameter("view", view);
        }
        if (hasText(filterByFormula)) {
            builder.addQueryParameter("filterByFormula", filterByFormula);
        }
        if (maxRecords != null) {
            builder.addQueryParameter("maxRecords", Integer.toString(maxRecords));
        }
        for (String fieldName : fieldNames) {
            builder.addQueryParameter("fields[]", fieldName);
        }
        if (hasText(sortField)) {
            builder.addQueryParameter("sort[0][field]", sortField);
        }
        if (hasText(sortDirection)) {
            builder.addQueryParameter("sort[0][direction]", sortDirection);
        }

        Request request = authorizedRequest(builder.build())
                .get()
                .build();
        JsonNode root = executeJson(request);
        return parseListResponse(root);
    }

    public AirtableRecord getRecord(String table, String recordId) {
        Request request = authorizedRequest(createRecordUrl(table, recordId))
                .get()
                .build();
        return parseRecord(executeJson(request));
    }

    public AirtableRecord createRecord(String table, Map<String, Object> fields, boolean typecast) {
        RequestBody body = buildWriteBody(fields, typecast);
        Request request = authorizedRequest(createTableUrlBuilder(table).build())
                .post(body)
                .build();
        return parseRecord(executeJson(request));
    }

    public AirtableRecord updateRecord(String table, String recordId, Map<String, Object> fields, boolean typecast) {
        RequestBody body = buildWriteBody(fields, typecast);
        Request request = authorizedRequest(createRecordUrl(table, recordId))
                .patch(body)
                .build();
        return parseRecord(executeJson(request));
    }

    public AirtableDeleteResponse deleteRecord(String table, String recordId) {
        Request request = authorizedRequest(createRecordUrl(table, recordId))
                .delete()
                .build();
        JsonNode root = executeJson(request);
        return new AirtableDeleteResponse(
                readText(root.get("id")),
                root.path("deleted").asBoolean(false));
    }

    protected Response executeRequest(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }

    private Request.Builder authorizedRequest(HttpUrl url) {
        AirtablePluginConfig config = configService.getConfig();
        return new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.getApiToken());
    }

    private RequestBody buildWriteBody(Map<String, Object> fields, boolean typecast) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fields", fields);
            payload.put("typecast", typecast);
            return RequestBody.create(objectMapper.writeValueAsString(payload), APPLICATION_JSON);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize Airtable request body", ex);
        }
    }

    private JsonNode executeJson(Request request) {
        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new AirtableApiException(response.code(), extractErrorMessage(response.code(), responseBody));
            }
            if (responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (AirtableApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new AirtableTransportException("Airtable transport failed: " + ex.getMessage(), ex);
        }
    }

    private AirtableListResponse parseListResponse(JsonNode root) {
        List<AirtableRecord> records = new ArrayList<>();
        JsonNode recordsNode = root.get("records");
        if (recordsNode != null && recordsNode.isArray()) {
            for (JsonNode recordNode : recordsNode) {
                records.add(parseRecord(recordNode));
            }
        }
        return new AirtableListResponse(records, readText(root.get("offset")));
    }

    private AirtableRecord parseRecord(JsonNode node) {
        JsonNode fieldsNode = node.get("fields");
        Map<String, Object> fields = fieldsNode != null && fieldsNode.isObject()
                ? objectMapper.convertValue(fieldsNode, MAP_TYPE)
                : Map.of();
        return new AirtableRecord(
                readText(node.get("id")),
                readText(node.get("createdTime")),
                fields);
    }

    private String extractErrorMessage(int statusCode, String responseBody) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode errorNode = root.get("error");
                if (errorNode != null) {
                    if (errorNode.isTextual()) {
                        return errorNode.asText();
                    }
                    if (errorNode.isObject() && errorNode.hasNonNull("message")) {
                        return errorNode.get("message").asText();
                    }
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

    private HttpUrl createRecordUrl(String table, String recordId) {
        return createTableUrlBuilder(table)
                .addPathSegment(recordId)
                .build();
    }

    private HttpUrl.Builder createBaseUrlBuilder() {
        AirtablePluginConfig config = configService.getConfig();
        URI uri = URI.create(config.getApiBaseUrl());
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Invalid Airtable API base URL: " + config.getApiBaseUrl());
        }
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme(uri.getScheme())
                .host(uri.getHost());
        if (uri.getPort() != -1) {
            builder.port(uri.getPort());
        }
        String path = uri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            for (String segment : path.split("/")) {
                if (!segment.isBlank()) {
                    builder.addPathSegment(segment);
                }
            }
        }
        builder.addPathSegment("v0");
        builder.addPathSegment(config.getBaseId());
        return builder;
    }

    private String readText(JsonNode node) {
        return node != null && !node.isNull() ? node.asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AirtableRecord(String id, String createdTime, Map<String, Object> fields) {
    }

    public record AirtableListResponse(List<AirtableRecord> records, String offset) {
    }

    public record AirtableDeleteResponse(String id, boolean deleted) {
    }
}
