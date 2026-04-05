package me.golemcore.plugins.golemcore.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class NotionApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    private final NotionPluginConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotionApiClient(NotionPluginConfigService configService) {
        this.configService = configService;
    }

    public String retrievePageTitle(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        JsonNode page = getJson("/v1/pages/" + pageId);
        return extractPageTitle(page);
    }

    public List<NotionPageSummary> listChildPages(String parentPageId) {
        return listChildItems(parentPageId).stream()
                .filter(child -> "page".equals(child.kind()))
                .map(child -> new NotionPageSummary(child.id(), child.title(), child.url()))
                .toList();
    }

    public List<NotionChildSummary> listChildItems(String parentPageId) {
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new IllegalArgumentException("parentPageId is required");
        }
        List<NotionChildSummary> items = new ArrayList<>();
        String nextCursor = "";
        boolean hasMore;
        do {
            StringBuilder pathBuilder = new StringBuilder("/v1/blocks/")
                    .append(parentPageId)
                    .append("/children?page_size=100");
            if (!nextCursor.isBlank()) {
                pathBuilder.append("&start_cursor=")
                        .append(URLEncoder.encode(nextCursor, StandardCharsets.UTF_8));
            }
            JsonNode response = getJson(pathBuilder.toString());
            for (JsonNode result : response.path("results")) {
                String type = result.path("type").asText("");
                if ("child_page".equals(type)) {
                    items.add(new NotionChildSummary(
                            result.path("id").asText(),
                            result.path("child_page").path("title").asText(""),
                            result.path("url").asText(""),
                            "page"));
                    continue;
                }
                if ("child_database".equals(type)) {
                    items.add(new NotionChildSummary(
                            result.path("id").asText(),
                            result.path("child_database").path("title").asText(""),
                            result.path("url").asText(""),
                            "database"));
                }
            }
            hasMore = response.path("has_more").asBoolean(false);
            nextCursor = hasMore ? response.path("next_cursor").asText("") : "";
        } while (hasMore && !nextCursor.isBlank());
        return List.copyOf(items);
    }

    public String retrievePageMarkdown(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        JsonNode response = getJson("/v1/pages/" + pageId + "/markdown");
        return response.path("markdown").asText("");
    }

    public NotionPageSummary createChildPage(String parentPageId, String title, String markdown) {
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new IllegalArgumentException("parentPageId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        JsonNode response = sendJson("POST", "/v1/pages", Map.of(
                "parent", Map.of("type", "page_id", "page_id", parentPageId),
                "properties", Map.of(
                        "title", Map.of(
                                "title", List.of(Map.of(
                                        "text", Map.of("content", title))))),
                "markdown", markdown != null ? markdown : ""));
        return toPageSummary(response);
    }

    public void updatePageMarkdown(String pageId, String markdown) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        sendJson("PATCH", "/v1/pages/" + pageId + "/markdown", Map.of(
                "type", "replace_content",
                "replace_content", Map.of("new_str", markdown != null ? markdown : "")));
    }

    public void archivePage(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        sendJson("PATCH", "/v1/pages/" + pageId, Map.of("archived", true));
    }

    public void movePage(String pageId, String targetParentPageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        if (targetParentPageId == null || targetParentPageId.isBlank()) {
            throw new IllegalArgumentException("targetParentPageId is required");
        }
        sendJson("POST", "/v1/pages/" + pageId + "/move", Map.of(
                "parent", Map.of("page_id", targetParentPageId)));
    }

    public void renamePage(String pageId, String title) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        sendJson("PATCH", "/v1/pages/" + pageId, Map.of(
                "properties", Map.of(
                        "title", Map.of(
                                "title", List.of(Map.of(
                                        "text", Map.of("content", title)))))));
    }

    public NotionDatabaseSummary createDatabase(
            String parentPageId,
            String title,
            String description,
            Map<String, Object> properties,
            boolean inline,
            String iconEmoji,
            String coverExternalUrl) {
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new IllegalArgumentException("parentPageId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("type", "page_id", "page_id", parentPageId));
        payload.put("title", richTextArray(title));
        if (description != null && !description.isBlank()) {
            payload.put("description", richTextArray(description));
        }
        payload.put("is_inline", inline);
        if (properties != null && !properties.isEmpty()) {
            payload.put("initial_data_source", Map.of("properties", properties));
        }
        if (iconEmoji != null && !iconEmoji.isBlank()) {
            payload.put("icon", Map.of("type", "emoji", "emoji", iconEmoji));
        }
        if (coverExternalUrl != null && !coverExternalUrl.isBlank()) {
            payload.put("cover", Map.of(
                    "type", "external",
                    "external", Map.of("url", coverExternalUrl)));
        }
        JsonNode response = sendJson("POST", "/v1/databases", payload);
        return toDatabaseSummary(response);
    }

    public NotionDatabaseSummary retrieveDatabase(String databaseId) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalArgumentException("databaseId is required");
        }
        return toDatabaseSummary(getJson("/v1/databases/" + databaseId));
    }

    public NotionDatabaseSummary updateDatabase(
            String databaseId,
            String title,
            String description,
            String iconEmoji,
            String coverExternalUrl) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalArgumentException("databaseId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (title != null) {
            payload.put("title", richTextArray(title));
        }
        if (description != null) {
            payload.put("description", richTextArray(description));
        }
        if (iconEmoji != null) {
            payload.put("icon", iconEmoji.isBlank()
                    ? null
                    : Map.of("type", "emoji", "emoji", iconEmoji));
        }
        if (coverExternalUrl != null) {
            payload.put("cover", coverExternalUrl.isBlank()
                    ? null
                    : Map.of("type", "external", "external", Map.of("url", coverExternalUrl)));
        }
        JsonNode response = sendJson("PATCH", "/v1/databases/" + databaseId, payload);
        return toDatabaseSummary(response);
    }

    public NotionDataSourceSummary createDataSource(
            String databaseId,
            String title,
            Map<String, Object> properties,
            String iconEmoji) {
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalArgumentException("databaseId is required");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties are required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("type", "database_id", "database_id", databaseId));
        payload.put("properties", properties);
        if (title != null && !title.isBlank()) {
            payload.put("title", richTextArray(title));
        }
        if (iconEmoji != null && !iconEmoji.isBlank()) {
            payload.put("icon", Map.of("type", "emoji", "emoji", iconEmoji));
        }
        JsonNode response = sendJson("POST", "/v1/data_sources", payload);
        return toDataSourceSummary(response);
    }

    public NotionDataSourceSummary retrieveDataSource(String dataSourceId) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        return toDataSourceSummary(getJson("/v1/data_sources/" + dataSourceId));
    }

    public NotionDataSourceSummary updateDataSource(
            String dataSourceId,
            String title,
            Map<String, Object> properties,
            String iconEmoji) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (title != null) {
            payload.put("title", richTextArray(title));
        }
        if (properties != null) {
            payload.put("properties", properties);
        }
        if (iconEmoji != null) {
            payload.put("icon", iconEmoji.isBlank()
                    ? null
                    : Map.of("type", "emoji", "emoji", iconEmoji));
        }
        JsonNode response = sendJson("PATCH", "/v1/data_sources/" + dataSourceId, payload);
        return toDataSourceSummary(response);
    }

    public NotionDataSourceQueryResult queryDataSource(
            String dataSourceId,
            String filterJson,
            String sortsJson,
            Integer limit,
            String cursor) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (filterJson != null && !filterJson.isBlank()) {
            payload.put("filter", parseJsonObject(filterJson, "filterJson"));
        }
        if (sortsJson != null && !sortsJson.isBlank()) {
            payload.put("sorts", parseJsonArray(sortsJson, "sortsJson"));
        }
        if (limit != null && limit > 0) {
            payload.put("page_size", limit);
        }
        if (cursor != null && !cursor.isBlank()) {
            payload.put("start_cursor", cursor);
        }
        JsonNode response = sendJson("POST", "/v1/data_sources/" + dataSourceId + "/query", payload);
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            results.add(pageSummaryMap(item));
        }
        return new NotionDataSourceQueryResult(
                dataSourceId,
                results.size(),
                response.path("has_more").asBoolean(false),
                response.path("next_cursor").asText(""),
                List.copyOf(results));
    }

    public NotionPageSummary createDataSourceEntry(
            String dataSourceId,
            String propertiesJson,
            String markdown,
            String contentJson,
            String iconEmoji,
            String coverExternalUrl) {
        if (dataSourceId == null || dataSourceId.isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("type", "data_source_id", "data_source_id", dataSourceId));
        payload.put("properties", parseJsonObject(propertiesJson, "propertiesJson"));
        applyContentPayload(payload, markdown, contentJson);
        if (iconEmoji != null && !iconEmoji.isBlank()) {
            payload.put("icon", Map.of("type", "emoji", "emoji", iconEmoji));
        }
        if (coverExternalUrl != null && !coverExternalUrl.isBlank()) {
            payload.put("cover", Map.of("type", "external", "external", Map.of("url", coverExternalUrl)));
        }
        JsonNode response = sendJson("POST", "/v1/pages", payload);
        return toPageSummary(response);
    }

    public NotionPageSummary updateDataSourceEntry(
            String pageId,
            String propertiesJson,
            String iconEmoji,
            String coverExternalUrl) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (propertiesJson != null) {
            payload.put("properties", parseJsonObject(propertiesJson, "propertiesJson"));
        }
        if (iconEmoji != null) {
            payload.put("icon", iconEmoji.isBlank()
                    ? null
                    : Map.of("type", "emoji", "emoji", iconEmoji));
        }
        if (coverExternalUrl != null) {
            payload.put("cover", coverExternalUrl.isBlank()
                    ? null
                    : Map.of("type", "external", "external", Map.of("url", coverExternalUrl)));
        }
        JsonNode response = sendJson("PATCH", "/v1/pages/" + pageId, payload);
        return toPageSummary(response);
    }

    public NotionPageDetails retrievePageDetails(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        JsonNode page = getJson("/v1/pages/" + pageId);
        return new NotionPageDetails(
                page.path("id").asText(),
                extractPageTitle(page),
                page.path("url").asText(""),
                extractPageFiles(page),
                extractPropertiesSummary(page.path("properties")));
    }

    public NotionFileUploadSummary createFileUpload(
            String mode,
            String filename,
            String contentType,
            Integer numberOfParts,
            String externalUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (mode != null && !mode.isBlank()) {
            payload.put("mode", mode);
        }
        if (filename != null && !filename.isBlank()) {
            payload.put("filename", filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            payload.put("content_type", contentType);
        }
        if (numberOfParts != null && numberOfParts > 0) {
            payload.put("number_of_parts", numberOfParts);
        }
        if (externalUrl != null && !externalUrl.isBlank()) {
            payload.put("external_url", externalUrl);
        }
        JsonNode response = sendJson("POST", "/v1/file_uploads", payload);
        return toFileUploadSummary(response);
    }

    public NotionFileUploadSummary uploadFileContent(String fileUploadId, Path filePath, String contentType) {
        if (fileUploadId == null || fileUploadId.isBlank()) {
            throw new IllegalArgumentException("fileUploadId is required");
        }
        if (filePath == null) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            Path leafPath = filePath.getFileName();
            String fileName = leafPath == null ? "upload.bin" : leafPath.toString();
            MediaType mediaType = contentType != null && !contentType.isBlank()
                    ? MediaType.get(contentType)
                    : OCTET_STREAM;
            MultipartBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, RequestBody.create(bytes, mediaType))
                    .build();
            JsonNode response = sendMultipart("POST", "/v1/file_uploads/" + fileUploadId + "/send", body);
            return toFileUploadSummary(response);
        } catch (IOException ex) {
            throw new NotionTransportException("Failed to read upload file: " + ex.getMessage(), ex);
        }
    }

    public NotionFileUploadSummary completeFileUpload(String fileUploadId) {
        if (fileUploadId == null || fileUploadId.isBlank()) {
            throw new IllegalArgumentException("fileUploadId is required");
        }
        JsonNode response = sendJson("POST", "/v1/file_uploads/" + fileUploadId + "/complete", Map.of());
        return toFileUploadSummary(response);
    }

    public NotionFileUploadSummary retrieveFileUpload(String fileUploadId) {
        if (fileUploadId == null || fileUploadId.isBlank()) {
            throw new IllegalArgumentException("fileUploadId is required");
        }
        return toFileUploadSummary(getJson("/v1/file_uploads/" + fileUploadId));
    }

    public List<NotionFileUploadSummary> listFileUploads(String status, Integer limit, String cursor) {
        StringBuilder pathBuilder = new StringBuilder("/v1/file_uploads");
        List<String> queryParts = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            queryParts.add("status=" + URLEncoder.encode(status, StandardCharsets.UTF_8));
        }
        if (limit != null && limit > 0) {
            queryParts.add("page_size=" + limit);
        }
        if (cursor != null && !cursor.isBlank()) {
            queryParts.add("start_cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
        }
        if (!queryParts.isEmpty()) {
            pathBuilder.append('?').append(String.join("&", queryParts));
        }
        JsonNode response = getJson(pathBuilder.toString());
        List<NotionFileUploadSummary> uploads = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            uploads.add(toFileUploadSummary(item));
        }
        return List.copyOf(uploads);
    }

    public NotionPageSummary appendFileBlock(
            String pageId,
            String fileUploadId,
            String externalUrl,
            String fileName,
            String caption,
            String blockType) {
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId is required");
        }
        String normalizedBlockType = normalizeBlockType(blockType);
        Map<String, Object> blockPayload = new LinkedHashMap<>();
        blockPayload.put("object", "block");
        blockPayload.put("type", normalizedBlockType);
        blockPayload.put(normalizedBlockType, buildMediaPayload(
                fileUploadId,
                externalUrl,
                fileName,
                caption,
                supportsFileName(normalizedBlockType)));
        JsonNode response = sendJson("PATCH", "/v1/blocks/" + pageId + "/children", Map.of(
                "children", List.of(blockPayload)));
        JsonNode firstResult = response.path("results").isArray() && response.path("results").size() > 0
                ? response.path("results").get(0)
                : objectMapper.createObjectNode();
        return new NotionPageSummary(
                firstResult.path("id").asText(""),
                fileName != null ? fileName : normalizedBlockType,
                firstResult.path("url").asText(""));
    }

    private JsonNode getJson(String path) {
        return sendJson("GET", path, null);
    }

    private JsonNode sendJson(String method, String path, Object body) {
        NotionPluginConfig config = requireConfiguredClient();
        Request.Builder builder = baseRequestBuilder(config, path);
        try {
            if ("GET".equals(method)) {
                builder.get();
            } else {
                String jsonBody = objectMapper.writeValueAsString(body != null ? body : Map.of());
                builder.header("Content-Type", "application/json");
                builder.method(method, RequestBody.create(jsonBody, JSON));
            }
        } catch (IOException e) {
            throw new NotionTransportException("Notion request serialization failed: " + e.getMessage(), e);
        }
        return execute(builder.build(), config);
    }

    private JsonNode sendMultipart(String method, String path, MultipartBody body) {
        NotionPluginConfig config = requireConfiguredClient();
        Request.Builder builder = baseRequestBuilder(config, path)
                .method(method, body);
        return execute(builder.build(), config);
    }

    private Request.Builder baseRequestBuilder(NotionPluginConfig config, String path) {
        return new Request.Builder()
                .url(stripTrailingSlash(config.getBaseUrl()) + path)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Notion-Version", config.getApiVersion())
                .header("Accept", "application/json");
    }

    private JsonNode execute(Request request, NotionPluginConfig config) {
        try (Response response = client(config).newCall(request).execute();
                ResponseBody responseBody = response.body()) {
            String rawBody = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new NotionApiException(response.code(), errorMessage(rawBody, response.message()));
            }
            return rawBody.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new NotionTransportException("Notion transport failed: " + e.getMessage(), e);
        }
    }

    private NotionPluginConfig requireConfiguredClient() {
        NotionPluginConfig config = configService.getConfig();
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Notion API key is not configured.");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalStateException("Notion base URL is not configured.");
        }
        return config;
    }

    private OkHttpClient client(NotionPluginConfig config) {
        return new OkHttpClient.Builder()
                .callTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    private NotionPageSummary toPageSummary(JsonNode page) {
        return new NotionPageSummary(
                page.path("id").asText(),
                extractPageTitle(page),
                page.path("url").asText(""));
    }

    private NotionDatabaseSummary toDatabaseSummary(JsonNode database) {
        List<Map<String, Object>> dataSources = new ArrayList<>();
        for (JsonNode dataSource : database.path("data_sources")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", dataSource.path("id").asText(""));
            item.put("name", dataSource.path("name").asText(""));
            dataSources.add(item);
        }
        return new NotionDatabaseSummary(
                database.path("id").asText(),
                richTextPlainText(database.path("title")),
                database.path("url").asText(""),
                List.copyOf(dataSources));
    }

    private NotionDataSourceSummary toDataSourceSummary(JsonNode dataSource) {
        return new NotionDataSourceSummary(
                dataSource.path("id").asText(),
                richTextPlainText(dataSource.path("title")),
                dataSource.path("url").asText(""),
                extractDataSourceProperties(dataSource.path("properties")));
    }

    private NotionFileUploadSummary toFileUploadSummary(JsonNode fileUpload) {
        JsonNode contentLengthNode = fileUpload.path("content_length");
        Long contentLength = contentLengthNode.isNumber()
                ? Long.valueOf(contentLengthNode.longValue())
                : parseLong(contentLengthNode.asText(""));
        return new NotionFileUploadSummary(
                fileUpload.path("id").asText(),
                fileUpload.path("status").asText(""),
                fileUpload.path("filename").asText(""),
                fileUpload.path("content_type").asText(""),
                contentLength,
                fileUpload.path("upload_url").asText(""),
                fileUpload.path("expiry_time").asText(""));
    }

    private String extractPageTitle(JsonNode page) {
        JsonNode properties = page.path("properties");
        if (properties.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                JsonNode property = fields.next().getValue();
                if (!"title".equals(property.path("type").asText())) {
                    continue;
                }
                String title = property.path("title").findValuesAsText("plain_text").stream()
                        .reduce("", String::concat);
                if (!title.isBlank()) {
                    return title;
                }
            }
        }
        return richTextPlainText(page.path("title"));
    }

    private Map<String, Object> extractDataSourceProperties(JsonNode properties) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isObject()) {
            return result;
        }
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode property = field.getValue();
            Map<String, Object> propertySummary = new LinkedHashMap<>();
            propertySummary.put("id", property.path("id").asText(""));
            propertySummary.put("type", property.path("type").asText(""));
            JsonNode typePayload = property.path(property.path("type").asText(""));
            if (typePayload.isObject() && typePayload.has("format")) {
                propertySummary.put("format", typePayload.path("format").asText(""));
            }
            result.put(field.getKey(), propertySummary);
        }
        return result;
    }

    private List<NotionFileAttachmentSummary> extractPageFiles(JsonNode page) {
        List<NotionFileAttachmentSummary> files = new ArrayList<>();
        JsonNode properties = page.path("properties");
        if (!properties.isObject()) {
            return List.copyOf(files);
        }
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode property = field.getValue();
            if (!"files".equals(property.path("type").asText())) {
                continue;
            }
            for (JsonNode fileNode : property.path("files")) {
                files.add(toFileAttachment(fileNode, "property", field.getKey()));
            }
        }
        return List.copyOf(files);
    }

    private NotionFileAttachmentSummary toFileAttachment(JsonNode fileNode, String sourceKind, String sourceName) {
        String type = fileNode.path("type").asText("");
        JsonNode payload = fileNode.path(type);
        return new NotionFileAttachmentSummary(
                fileNode.path("name").asText(""),
                type,
                payload.path("url").asText(""),
                payload.path("expiry_time").asText(""),
                sourceKind,
                sourceName);
    }

    private Map<String, Object> extractPropertiesSummary(JsonNode properties) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isObject()) {
            return result;
        }
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), summarizePropertyValue(field.getValue()));
        }
        return result;
    }

    private Map<String, Object> summarizePropertyValue(JsonNode property) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String type = property.path("type").asText("");
        summary.put("type", type);
        if ("title".equals(type)) {
            summary.put("value", richTextPlainText(property.path("title")));
        } else if ("rich_text".equals(type)) {
            summary.put("value", richTextPlainText(property.path("rich_text")));
        } else if ("number".equals(type)) {
            summary.put("value", property.path("number").isNumber() ? property.path("number").numberValue() : null);
        } else if ("checkbox".equals(type)) {
            summary.put("value", property.path("checkbox").asBoolean(false));
        } else if ("url".equals(type) || "email".equals(type) || "phone_number".equals(type)) {
            summary.put("value", property.path(type).asText(""));
        } else if ("date".equals(type)) {
            summary.put("value", jsonNodeToJava(property.path("date")));
        } else if ("select".equals(type) || "status".equals(type)) {
            JsonNode selected = property.path(type);
            summary.put("value", selected.path("name").asText(selected.path("id").asText("")));
        } else if ("multi_select".equals(type)) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : property.path("multi_select")) {
                values.add(item.path("name").asText(item.path("id").asText("")));
            }
            summary.put("value", values);
        } else if ("files".equals(type)) {
            List<Map<String, Object>> values = new ArrayList<>();
            for (JsonNode item : property.path("files")) {
                Map<String, Object> fileSummary = new LinkedHashMap<>();
                fileSummary.put("name", item.path("name").asText(""));
                fileSummary.put("type", item.path("type").asText(""));
                JsonNode typePayload = item.path(item.path("type").asText(""));
                fileSummary.put("url", typePayload.path("url").asText(""));
                fileSummary.put("expiry_time", typePayload.path("expiry_time").asText(""));
                values.add(fileSummary);
            }
            summary.put("value", values);
        } else {
            summary.put("value", jsonNodeToJava(property.path(type)));
        }
        return summary;
    }

    private Map<String, Object> pageSummaryMap(JsonNode page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", page.path("id").asText(""));
        result.put("title", extractPageTitle(page));
        result.put("url", page.path("url").asText(""));
        result.put("properties", extractPropertiesSummary(page.path("properties")));
        return result;
    }

    private List<Map<String, Object>> richTextArray(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(Map.of("text", Map.of("content", text)));
    }

    private String richTextPlainText(JsonNode richTextNode) {
        if (richTextNode == null || !richTextNode.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : richTextNode) {
            String plainText = item.path("plain_text").asText("");
            if (!plainText.isBlank()) {
                builder.append(plainText);
                continue;
            }
            builder.append(item.path("text").path("content").asText(""));
        }
        return builder.toString();
    }

    private Map<String, Object> parseJsonObject(String rawJson, String fieldName) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (!node.isObject()) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object");
            }
            return objectMapper.convertValue(
                    node,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IOException ex) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON: " + ex.getMessage(), ex);
        }
    }

    private List<Object> parseJsonArray(String rawJson, String fieldName) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (!node.isArray()) {
                throw new IllegalArgumentException(fieldName + " must be a JSON array");
            }
            return objectMapper.convertValue(
                    node,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
        } catch (IOException ex) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON: " + ex.getMessage(), ex);
        }
    }

    private void applyContentPayload(Map<String, Object> payload, String markdown, String contentJson) {
        boolean hasMarkdown = markdown != null;
        boolean hasContent = contentJson != null && !contentJson.isBlank();
        if (hasMarkdown && hasContent) {
            throw new IllegalArgumentException("markdown and contentJson are mutually exclusive");
        }
        if (hasMarkdown) {
            payload.put("markdown", markdown);
            return;
        }
        if (hasContent) {
            payload.put("children", parseJsonArray(contentJson, "contentJson"));
        }
    }

    private Map<String, Object> buildMediaPayload(
            String fileUploadId,
            String externalUrl,
            String fileName,
            String caption,
            boolean supportsName) {
        if ((fileUploadId == null || fileUploadId.isBlank()) && (externalUrl == null || externalUrl.isBlank())) {
            throw new IllegalArgumentException("fileUploadId or externalUrl is required");
        }
        if (fileUploadId != null && !fileUploadId.isBlank() && externalUrl != null && !externalUrl.isBlank()) {
            throw new IllegalArgumentException("Provide either fileUploadId or externalUrl, not both");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (caption != null && !caption.isBlank()) {
            payload.put("caption", richTextArray(caption));
        }
        if (fileUploadId != null && !fileUploadId.isBlank()) {
            payload.put("type", "file_upload");
            payload.put("file_upload", Map.of("id", fileUploadId));
        } else {
            payload.put("type", "external");
            payload.put("external", Map.of("url", externalUrl));
        }
        if (supportsName && fileName != null && !fileName.isBlank()) {
            payload.put("name", fileName);
        }
        return payload;
    }

    private String normalizeBlockType(String blockType) {
        if (blockType == null || blockType.isBlank()) {
            return "file";
        }
        String normalized = blockType.trim().toLowerCase(Locale.ROOT);
        if (List.of("file", "image", "pdf", "audio", "video").contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported file block type: " + blockType);
    }

    private boolean supportsFileName(String blockType) {
        return "file".equals(blockType);
    }

    private Object jsonNodeToJava(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        if (node.isBinary()) {
            try {
                return Base64.getEncoder().encodeToString(node.binaryValue());
            } catch (IOException ex) {
                return node.asText("");
            }
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String errorMessage(String rawBody, String fallback) {
        try {
            JsonNode json = rawBody.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(rawBody);
            JsonNode message = json.path("message");
            if (!message.isMissingNode() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (IOException ignored) {
            // fall through to raw body or fallback message
        }
        return rawBody.isBlank() ? fallback : rawBody;
    }

    private String stripTrailingSlash(String url) {
        String stripped = url;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }
}
