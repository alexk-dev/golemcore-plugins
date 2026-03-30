package me.golemcore.plugins.golemcore.notion.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotionApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private NotionPluginConfigService configService;
    private NotionApiClient client;
    private Queue<StubResponse> responses;
    private List<CapturedRequest> requests;

    @BeforeEach
    void setUp() throws IOException {
        responses = new ArrayDeque<>();
        requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();

        configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(NotionPluginConfig.builder()
                .enabled(true)
                .baseUrl(baseUrl())
                .apiVersion("2026-03-11")
                .apiKey("secret")
                .rootPageId("root-page")
                .timeoutMs(5_000)
                .maxReadChars(12_000)
                .allowWrite(true)
                .allowDelete(true)
                .allowMove(true)
                .allowRename(true)
                .localIndexEnabled(false)
                .reindexSchedulePreset("disabled")
                .reindexCronExpression("")
                .ragSyncEnabled(false)
                .targetRagProviderId("")
                .ragCorpusId("notion")
                .build());
        client = new NotionApiClient(configService);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldRetrievePageTitleWithNotionHeaders() {
        respondJson(200, """
                {
                  "id": "root-page",
                  "properties": {
                    "title": {
                      "id": "title",
                      "type": "title",
                      "title": [
                        { "plain_text": "Knowledge Vault" }
                      ]
                    }
                  }
                }
                """);

        String title = client.retrievePageTitle("root-page");

        assertEquals("Knowledge Vault", title);
        CapturedRequest request = requests.getFirst();
        assertEquals("GET", request.method());
        assertEquals("/v1/pages/root-page", request.path());
        assertEquals("Bearer secret", request.authorization());
        assertEquals("2026-03-11", request.notionVersion());
    }

    @Test
    void shouldListChildPagesAcrossPagination() {
        respondJson(200, """
                {
                  "results": [
                    {
                      "id": "projects-id",
                      "type": "child_page",
                      "child_page": { "title": "Projects" }
                    },
                    {
                      "id": "ignored-block",
                      "type": "paragraph"
                    }
                  ],
                  "has_more": true,
                  "next_cursor": "next-1"
                }
                """);
        respondJson(200, """
                {
                  "results": [
                    {
                      "id": "inbox-id",
                      "type": "child_page",
                      "child_page": { "title": "Inbox" }
                    }
                  ],
                  "has_more": false
                }
                """);

        List<NotionPageSummary> pages = client.listChildPages("root-page");

        assertEquals(List.of("Projects", "Inbox"), pages.stream().map(NotionPageSummary::title).toList());
        assertEquals("/v1/blocks/root-page/children?page_size=100", requests.get(0).path());
        assertEquals("/v1/blocks/root-page/children?page_size=100&start_cursor=next-1", requests.get(1).path());
    }

    @Test
    void shouldRetrieveMarkdownFromMarkdownEndpoint() {
        respondJson(200, """
                {
                  "object": "page_markdown",
                  "id": "todo-id",
                  "markdown": "# Todo\\n\\nBody",
                  "truncated": false,
                  "unknown_block_ids": []
                }
                """);

        String markdown = client.retrievePageMarkdown("todo-id");

        assertEquals("# Todo\n\nBody", markdown);
        assertEquals("/v1/pages/todo-id/markdown", requests.getFirst().path());
    }

    @Test
    void shouldCreateChildPageWithExplicitTitleAndMarkdown() throws Exception {
        respondJson(200, """
                {
                  "id": "todo-id",
                  "url": "https://notion.so/todo-id",
                  "properties": {
                    "title": {
                      "id": "title",
                      "type": "title",
                      "title": [
                        { "plain_text": "Todo" }
                      ]
                    }
                  }
                }
                """);

        NotionPageSummary page = client.createChildPage("projects-id", "Todo", "# Todo\n\nBody");

        assertEquals("todo-id", page.id());
        assertEquals("Todo", page.title());
        JsonNode body = objectMapper.readTree(requests.getFirst().body());
        assertEquals("projects-id", body.path("parent").path("page_id").asText());
        assertEquals("# Todo\n\nBody", body.path("markdown").asText());
        assertEquals("Todo",
                body.path("properties").path("title").path("title").get(0).path("text").path("content").asText());
    }

    @Test
    void shouldReplaceWholePageContentWhenUpdatingMarkdown() throws Exception {
        respondJson(200, """
                {
                  "object": "page_markdown",
                  "id": "todo-id",
                  "markdown": "# Fresh",
                  "truncated": false,
                  "unknown_block_ids": []
                }
                """);

        client.updatePageMarkdown("todo-id", "# Fresh");

        CapturedRequest request = requests.getFirst();
        assertEquals("PATCH", request.method());
        assertEquals("/v1/pages/todo-id/markdown", request.path());
        JsonNode body = objectMapper.readTree(request.body());
        assertEquals("replace_content", body.path("type").asText());
        assertEquals("# Fresh", body.path("replace_content").path("new_str").asText());
    }

    @Test
    void shouldArchiveMoveAndRenamePagesUsingOfficialEndpoints() throws Exception {
        respondJson(200, "{\"id\":\"todo-id\"}");
        respondJson(200, "{\"id\":\"todo-id\"}");
        respondJson(200, "{\"id\":\"todo-id\"}");

        client.archivePage("todo-id");
        client.movePage("todo-id", "archive-id");
        client.renamePage("todo-id", "Done");

        assertEquals("PATCH", requests.get(0).method());
        assertEquals("/v1/pages/todo-id", requests.get(0).path());
        assertTrue(objectMapper.readTree(requests.get(0).body()).path("archived").asBoolean());

        assertEquals("POST", requests.get(1).method());
        assertEquals("/v1/pages/todo-id/move", requests.get(1).path());
        assertEquals("archive-id",
                objectMapper.readTree(requests.get(1).body()).path("parent").path("page_id").asText());

        assertEquals("PATCH", requests.get(2).method());
        assertEquals("/v1/pages/todo-id", requests.get(2).path());
        assertEquals("Done", objectMapper.readTree(requests.get(2).body())
                .path("properties").path("title").path("title").get(0).path("text").path("content").asText());
    }

    private void respondJson(int status, String body) {
        responses.add(new StubResponse(status, body));
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Notion-Version"),
                new String(requestBody, StandardCharsets.UTF_8)));
        StubResponse response = responses.remove();
        byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private record StubResponse(int status, String body) {
    }

    private record CapturedRequest(
            String method,
            String path,
            String authorization,
            String notionVersion,
            String body) {
    }
}
