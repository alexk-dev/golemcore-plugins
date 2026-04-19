package me.golemcore.plugins.golemcore.brain;

import me.golemcore.bot.testsupport.http.OkHttpMockEngine;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrainToolProviderTest {

    private OkHttpMockEngine httpEngine;
    private BrainPluginConfig config;
    private BrainToolProvider provider;

    @BeforeEach
    void setUp() {
        httpEngine = new OkHttpMockEngine();
        config = BrainPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://brain.example")
                .apiToken("brain-token")
                .defaultSpaceSlug("docs")
                .defaultIntellisearchLimit(3)
                .build();
        config.normalize();
        BrainPluginConfigService configService = new FixedBrainPluginConfigService(config);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(httpEngine)
                .build();
        provider = new BrainToolProvider(configService, client);
    }

    @Test
    void shouldExposeBrainOperationsIncludingIntellisearch() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> operation = (Map<?, ?>) properties.get("operation");

        assertEquals("golemcore_brain", definition.getName());
        assertEquals(List.of(
                "list_spaces",
                "list_tree",
                "search_pages",
                "get_search_status",
                "intellisearch",
                "read_page",
                "create_page",
                "update_page",
                "delete_page",
                "ensure_page",
                "move_page",
                "copy_page",
                "list_assets",
                "reindex_space",
                "reindex_all_spaces",
                "patch_page",
                "get_wiki_graph",
                "wiki_top_accessed",
                "wiki_tx"), operation.get("enum"));
    }

    @Test
    void shouldSearchPagesThroughBrainCrudApi() {
        httpEngine.enqueueJson(200, """
                {
                  "mode":"fts",
                  "semanticReady":false,
                  "hits":[
                    {"path":"ops/runbook","title":"Runbook","excerpt":"deploy safely","kind":"PAGE"}
                  ]
                }
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "search_pages",
                "query", "deploy",
                "limit", 2)).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Runbook"));
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/spaces/docs/search", request.target());
        assertEquals("Bearer brain-token", request.header("Authorization"));
        assertTrue(request.body().contains("\"query\":\"deploy\""));
        assertTrue(request.body().contains("\"mode\":\"auto\""));
        assertTrue(request.body().contains("\"limit\":2"));
    }

    @Test
    void shouldPassRequestedSearchModeToBrainSearchApi() {
        httpEngine.enqueueJson(200, """
                {
                  "mode":"hybrid",
                  "semanticReady":true,
                  "hits":[]
                }
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "search_pages",
                "query", "incident response",
                "search_mode", "hybrid")).join();

        assertTrue(result.isSuccess());
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertEquals("/api/spaces/docs/search", request.target());
        assertTrue(request.body().contains("\"mode\":\"hybrid\""));
    }

    @Test
    void shouldReadSearchStatusThroughBrainCrudApi() {
        httpEngine.enqueueJson(200, """
                {
                  "mode":"hybrid",
                  "ready":true,
                  "indexedDocuments":12,
                  "fullTextIndexedDocuments":12,
                  "embeddingDocuments":12,
                  "embeddingIndexedDocuments":10,
                  "staleDocuments":2,
                  "embeddingsReady":false,
                  "embeddingModelId":"text-embedding-3-small"
                }
                """);

        ToolResult result = provider.execute(Map.of("operation", "get_search_status")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("ready=true"));
        assertTrue(result.getOutput().contains("indexedDocuments=12"));
        assertTrue(result.getOutput().contains("embeddingsReady=false"));
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertEquals("GET", request.method());
        assertEquals("/api/spaces/docs/search/status", request.target());
    }

    @Test
    void shouldReadPageThroughBrainCrudApi() {
        httpEngine.enqueueJson(200, """
                {"path":"ops/runbook","title":"Runbook","content":"# Runbook","kind":"PAGE"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "read_page",
                "path", "ops/runbook")).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("# Runbook"));
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertEquals("GET", request.method());
        assertEquals("/api/spaces/docs/page?path=ops%2Frunbook", request.target());
    }

    @Test
    void shouldCreatePageThroughBrainCrudApiWhenWritesAllowed() {
        config.setAllowWrite(true);
        httpEngine.enqueueJson(200, """
                {"path":"ops","title":"Ops","content":"","kind":"SECTION"}
                """);
        httpEngine.enqueueJson(200, """
                {"path":"ops/runbook","title":"Runbook","content":"# Runbook","kind":"PAGE"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "create_page",
                "parent_path", "ops",
                "title", "Runbook",
                "slug", "runbook",
                "content", "# Runbook",
                "kind", "PAGE")).join();

        assertTrue(result.isSuccess());
        OkHttpMockEngine.CapturedRequest lookupRequest = httpEngine.takeRequest();
        OkHttpMockEngine.CapturedRequest createRequest = httpEngine.takeRequest();
        assertEquals("GET", lookupRequest.method());
        assertEquals("/api/spaces/docs/page?path=ops", lookupRequest.target());
        assertEquals("POST", createRequest.method());
        assertEquals("/api/spaces/docs/pages", createRequest.target());
        assertTrue(createRequest.body().contains("\"parentPath\":\"ops\""));
        assertTrue(createRequest.body().contains("\"title\":\"Runbook\""));
    }

    @Test
    void shouldResolveCreateParentPathBySlugifyingTitleWhenExactPathIsMissing() {
        config.setAllowWrite(true);
        httpEngine.enqueueJson(404, """
                {"error":"Page not found: golem notes"}
                """);
        httpEngine.enqueueJson(200, """
                {"path":"golem-notes","title":"Golem Notes","content":"","kind":"SECTION"}
                """);
        httpEngine.enqueueJson(200, """
                {"path":"golem-notes/test-note","title":"Test Note","content":"Body","kind":"PAGE"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "create_page",
                "parent_path", "golem notes",
                "title", "Test Note",
                "slug", "test-note",
                "content", "Body",
                "kind", "PAGE")).join();

        assertTrue(result.isSuccess());
        OkHttpMockEngine.CapturedRequest exactLookup = httpEngine.takeRequest();
        OkHttpMockEngine.CapturedRequest slugLookup = httpEngine.takeRequest();
        OkHttpMockEngine.CapturedRequest createRequest = httpEngine.takeRequest();
        assertEquals("/api/spaces/docs/page?path=golem%20notes", exactLookup.target());
        assertEquals("/api/spaces/docs/page?path=golem-notes", slugLookup.target());
        assertEquals("POST", createRequest.method());
        assertTrue(createRequest.body().contains("\"parentPath\":\"golem-notes\""));
    }

    @Test
    void shouldRejectCreateParentPathThatResolvesToPage() {
        config.setAllowWrite(true);
        httpEngine.enqueueJson(200, """
                {"path":"ops/runbook","title":"Runbook","content":"Body","kind":"PAGE"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "create_page",
                "parent_path", "ops/runbook",
                "title", "Child",
                "slug", "child",
                "content", "Body",
                "kind", "PAGE")).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Brain path is not a section: ops/runbook"));
        assertEquals(1, httpEngine.getRequestCount());
    }

    @Test
    void shouldExposeBrainApiErrorMessageWithoutRawJson() {
        httpEngine.enqueueJson(404, """
                {"error":"Section not found: golem notes"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "read_page",
                "path", "golem notes")).join();

        assertFalse(result.isSuccess());
        assertEquals("Brain API request failed with HTTP 404: Section not found: golem notes", result.getError());
    }

    @Test
    void shouldReturnClearErrorWhenCreateParentSectionCannotBeResolved() {
        config.setAllowWrite(true);
        httpEngine.enqueueJson(404, """
                {"error":"Page not found: Missing Section"}
                """);
        httpEngine.enqueueJson(404, """
                {"error":"Page not found: missing-section"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "create_page",
                "parent_path", "Missing Section",
                "title", "Test Note",
                "slug", "test-note",
                "content", "Body",
                "kind", "PAGE")).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Brain section not found: Missing Section"));
        assertEquals(2, httpEngine.getRequestCount());
    }

    @Test
    void shouldDenyWriteOperationsWhenWritesDisabled() {
        ToolResult result = provider.execute(Map.of(
                "operation", "delete_page",
                "path", "ops/runbook")).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("mutating operations are disabled"));
        assertEquals(0, httpEngine.getRequestCount());
    }

    @Test
    void shouldUseConfiguredDynamicEndpointForIntellisearch() {
        config.setDynamicApiSlug("brain-relevance");
        httpEngine.enqueueJson(200, """
                {
                  "apiSlug":"brain-relevance",
                  "result":{"documents":[{"path":"ops/runbook","title":"Runbook","score":0.91}]},
                  "rawResponse":"{\\"documents\\":[]}",
                  "iterations":2,
                  "toolCallCount":1
                }
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "intellisearch",
                "context", "Need deployment rollback docs",
                "query", "rollback",
                "limit", 5)).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("brain-relevance"));
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/spaces/docs/dynamic-apis/brain-relevance/run", request.target());
        assertTrue(request.body().contains("Need deployment rollback docs"));
        assertTrue(request.body().contains("rollback"));
    }

    @Test
    void shouldFallbackToSearchAndReadPagesForIntellisearchWhenDynamicEndpointIsAbsent() {
        httpEngine.enqueueJson(200, """
                {
                  "mode":"fts-fallback",
                  "semanticReady":false,
                  "fallbackReason":"embedding-model-not-configured",
                  "hits":[
                    {"path":"ops/runbook","title":"Runbook","excerpt":"rollback steps","kind":"PAGE"}
                  ]
                }
                """);
        httpEngine.enqueueJson(200, """
                {"path":"ops/runbook","title":"Runbook","content":"Rollback by reverting the release.","kind":"PAGE"}
                """);

        ToolResult result = provider.execute(Map.of(
                "operation", "intellisearch",
                "context", "Need deployment rollback docs",
                "limit", 1)).join();

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Rollback by reverting"));
        OkHttpMockEngine.CapturedRequest searchRequest = httpEngine.takeRequest();
        OkHttpMockEngine.CapturedRequest readRequest = httpEngine.takeRequest();
        assertEquals("POST", searchRequest.method());
        assertEquals("/api/spaces/docs/search", searchRequest.target());
        assertTrue(searchRequest.body().contains("\"query\":\"Need deployment rollback docs\""));
        assertTrue(searchRequest.body().contains("\"mode\":\"hybrid\""));
        assertTrue(searchRequest.body().contains("\"limit\":1"));
        assertEquals("/api/spaces/docs/page?path=ops%2Frunbook", readRequest.target());
    }

    @Test
    void shouldQueueReindexRequestsWhenMutatingOperationsAreAllowed() {
        config.setAllowWrite(true);
        httpEngine.enqueueJson(202, """
                {"status":"queued","spacesQueued":1}
                """);
        httpEngine.enqueueJson(202, """
                {"status":"queued","spacesQueued":3}
                """);

        ToolResult spaceResult = provider.execute(Map.of("operation", "reindex_space")).join();
        ToolResult allResult = provider.execute(Map.of("operation", "reindex_all_spaces")).join();

        assertTrue(spaceResult.isSuccess());
        assertTrue(allResult.isSuccess());
        assertTrue(spaceResult.getOutput().contains("space docs"));
        assertTrue(allResult.getOutput().contains("3 space"));
        OkHttpMockEngine.CapturedRequest spaceRequest = httpEngine.takeRequest();
        OkHttpMockEngine.CapturedRequest allRequest = httpEngine.takeRequest();
        assertEquals("POST", spaceRequest.method());
        assertEquals("/api/admin/spaces/docs/reindex", spaceRequest.target());
        assertEquals("POST", allRequest.method());
        assertEquals("/api/admin/spaces/reindex", allRequest.target());
    }

    @Test
    void shouldRequireConfiguredBrainBaseUrl() {
        config.setBaseUrl(" ");
        config.normalize();

        assertFalse(provider.isEnabled());
        ToolResult result = provider.execute(Map.of("operation", "list_spaces")).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("disabled or base URL is missing"));
    }

    @Test
    void shouldRejectMissingOperation() {
        ToolResult result = provider.execute(Map.of()).join();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("operation is required"));
    }

    private static class FixedBrainPluginConfigService extends BrainPluginConfigService {
        private final BrainPluginConfig config;

        FixedBrainPluginConfigService(BrainPluginConfig config) {
            super(null);
            this.config = config;
        }

        @Override
        public BrainPluginConfig getConfig() {
            config.normalize();
            return config;
        }
    }
}
