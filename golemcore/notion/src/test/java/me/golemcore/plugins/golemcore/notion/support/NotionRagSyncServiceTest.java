package me.golemcore.plugins.golemcore.notion.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.golemcore.plugin.api.extension.model.rag.RagCorpusRef;
import me.golemcore.plugin.api.extension.model.rag.RagDocument;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionCapabilities;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionResult;
import me.golemcore.plugin.api.runtime.RagIngestionService;
import me.golemcore.plugin.api.runtime.model.RagIngestionTargetDescriptor;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class NotionRagSyncServiceTest {

    private NotionPluginConfigService configService;
    private RagIngestionService ragIngestionService;
    private StubNotionApiClient apiClient;
    private NotionRagSyncService service;

    @BeforeEach
    void setUp() {
        configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(config(true));
        ragIngestionService = mock(RagIngestionService.class);
        when(ragIngestionService.listInstalledTargets()).thenReturn(List.of(new RagIngestionTargetDescriptor(
                "golemcore/lightrag",
                "golemcore/lightrag",
                "golemcore/lightrag",
                new RagIngestionCapabilities(true, true, true, 2))));
        apiClient = new StubNotionApiClient(configService);
        service = new NotionRagSyncService(configService, apiClient, ragIngestionService);
    }

    @Test
    void shouldUpsertSingleDocumentForMutationSync() {
        when(ragIngestionService.upsertDocuments(
                eq("golemcore/lightrag"),
                eq(new RagCorpusRef("notion", "notion")),
                eq(List.of(new RagDocument(
                        "todo-page",
                        "Todo",
                        "Projects/Todo",
                        "# Todo",
                        "https://notion.so/todo-page",
                        Map.of("source", "notion", "page_id", "todo-page"))))))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagIngestionResult("accepted", 1, 0, null, "ok")));

        service.upsertDocument("todo-page", "Projects/Todo", "Todo", "# Todo", "https://notion.so/todo-page");

        verify(ragIngestionService).upsertDocuments(
                "golemcore/lightrag",
                new RagCorpusRef("notion", "notion"),
                List.of(new RagDocument(
                        "todo-page",
                        "Todo",
                        "Projects/Todo",
                        "# Todo",
                        "https://notion.so/todo-page",
                        Map.of("source", "notion", "page_id", "todo-page"))));
    }

    @Test
    void shouldMarkFullReindexRequiredWhenDeleteIsUnsupported() {
        when(ragIngestionService.listInstalledTargets()).thenReturn(List.of(new RagIngestionTargetDescriptor(
                "golemcore/lightrag",
                "golemcore/lightrag",
                "golemcore/lightrag",
                new RagIngestionCapabilities(false, false, false, 32))));

        service.deleteDocument("todo-page");

        assertTrue(service.isFullReindexRequired());
    }

    @Test
    void shouldResetAndBulkUpsertDocumentsDuringFullReindex() {
        apiClient.pageTitles.put("root-page", "Workspace");
        apiClient.pageMarkdown.put("root-page", "# Workspace");
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.pageMarkdown.put("projects-page", "# Projects");
        apiClient.addChild("projects-page", "todo-page", "Todo");
        apiClient.pageMarkdown.put("todo-page", "# Todo");
        when(ragIngestionService.resetCorpus(
                "golemcore/lightrag",
                new RagCorpusRef("notion", "notion")))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagIngestionResult("accepted", 0, 0, null, "ok")));
        when(ragIngestionService.upsertDocuments(
                eq("golemcore/lightrag"),
                eq(new RagCorpusRef("notion", "notion")),
                eq(List.of(
                        new RagDocument(
                                "root-page",
                                "Workspace",
                                "",
                                "# Workspace",
                                "",
                                Map.of("source", "notion", "page_id", "root-page")),
                        new RagDocument(
                                "projects-page",
                                "Projects",
                                "Projects",
                                "# Projects",
                                "https://notion.so/projects-page",
                                Map.of("source", "notion", "page_id", "projects-page"))))))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagIngestionResult("accepted", 2, 0, null, "ok")));
        when(ragIngestionService.upsertDocuments(
                eq("golemcore/lightrag"),
                eq(new RagCorpusRef("notion", "notion")),
                eq(List.of(new RagDocument(
                        "todo-page",
                        "Todo",
                        "Projects/Todo",
                        "# Todo",
                        "https://notion.so/todo-page",
                        Map.of("source", "notion", "page_id", "todo-page"))))))
                .thenReturn(CompletableFuture.completedFuture(
                        new RagIngestionResult("accepted", 1, 0, null, "ok")));

        int synced = service.reindexAll();

        assertEquals(3, synced);
        assertFalse(service.isFullReindexRequired());
        verify(ragIngestionService).resetCorpus("golemcore/lightrag", new RagCorpusRef("notion", "notion"));
    }

    private NotionPluginConfig config(boolean ragSyncEnabled) {
        return NotionPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://api.notion.com")
                .apiVersion("2026-03-11")
                .apiKey("secret")
                .rootPageId("root-page")
                .timeoutMs(30_000)
                .maxReadChars(12_000)
                .allowWrite(true)
                .allowDelete(true)
                .allowMove(true)
                .allowRename(true)
                .localIndexEnabled(false)
                .reindexSchedulePreset("disabled")
                .reindexCronExpression("")
                .ragSyncEnabled(ragSyncEnabled)
                .targetRagProviderId("golemcore/lightrag")
                .ragCorpusId("notion")
                .build();
    }

    private static final class StubNotionApiClient extends NotionApiClient {

        private final Map<String, List<NotionPageSummary>> childrenByParent = new LinkedHashMap<>();
        private final Map<String, String> pageMarkdown = new LinkedHashMap<>();
        private final Map<String, String> pageTitles = new LinkedHashMap<>();

        private StubNotionApiClient(NotionPluginConfigService configService) {
            super(configService);
        }

        @Override
        public String retrievePageTitle(String pageId) {
            return pageTitles.getOrDefault(pageId, "");
        }

        @Override
        public List<NotionPageSummary> listChildPages(String parentPageId) {
            return childrenByParent.getOrDefault(parentPageId, List.of());
        }

        @Override
        public String retrievePageMarkdown(String pageId) {
            return pageMarkdown.getOrDefault(pageId, "");
        }

        private void addChild(String parentPageId, String pageId, String title) {
            childrenByParent.computeIfAbsent(parentPageId, ignored -> new ArrayList<>())
                    .add(new NotionPageSummary(pageId, title, "https://notion.so/" + pageId));
        }
    }
}
