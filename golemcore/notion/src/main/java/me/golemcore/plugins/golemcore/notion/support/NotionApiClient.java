package me.golemcore.plugins.golemcore.notion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class NotionApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

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
        if (parentPageId == null || parentPageId.isBlank()) {
            throw new IllegalArgumentException("parentPageId is required");
        }
        List<NotionPageSummary> pages = new ArrayList<>();
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
                if (!"child_page".equals(result.path("type").asText())) {
                    continue;
                }
                pages.add(new NotionPageSummary(
                        result.path("id").asText(),
                        result.path("child_page").path("title").asText(),
                        result.path("url").asText("")));
            }
            hasMore = response.path("has_more").asBoolean(false);
            nextCursor = hasMore ? response.path("next_cursor").asText("") : "";
        } while (hasMore && !nextCursor.isBlank());
        return pages;
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
                "parent", Map.of("page_id", parentPageId),
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

    private JsonNode getJson(String path) {
        return sendJson("GET", path, null);
    }

    private JsonNode sendJson(String method, String path, Object body) {
        NotionPluginConfig config = requireConfiguredClient();
        Request.Builder builder = new Request.Builder()
                .url(stripTrailingSlash(config.getBaseUrl()) + path)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Notion-Version", config.getApiVersion())
                .header("Accept", "application/json");
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

        try (Response response = client(config).newCall(builder.build()).execute();
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

    private String extractPageTitle(JsonNode page) {
        JsonNode properties = page.path("properties");
        if (properties.isObject()) {
            var fields = properties.fields();
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
        return "";
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
