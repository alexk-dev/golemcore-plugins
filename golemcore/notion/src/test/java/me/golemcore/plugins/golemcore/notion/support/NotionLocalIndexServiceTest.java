package me.golemcore.plugins.golemcore.notion.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class NotionLocalIndexServiceTest {

    @TempDir
    Path tempDir;

    private NotionPluginConfigService configService;
    private StubNotionApiClient apiClient;
    private NotionLocalIndexService service;

    @BeforeEach
    void setUp() {
        configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(NotionPluginConfig.builder()
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
                .localIndexEnabled(true)
                .reindexSchedulePreset("disabled")
                .reindexCronExpression("")
                .ragSyncEnabled(false)
                .targetRagProviderId("")
                .ragCorpusId("notion")
                .build());
        apiClient = new StubNotionApiClient(configService);
        service = new NotionLocalIndexService(apiClient, configService, new NotionStoragePaths(tempDir));
    }

    @Test
    void shouldReindexVaultTreeAndSearchByPseudoPathPrefix() {
        apiClient.rootTitle = "Workspace";
        apiClient.pageMarkdown.put("root-page", "Workspace overview");
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.addChild("root-page", "archive-page", "Archive");
        apiClient.pageMarkdown.put("projects-page", "Project overview");
        apiClient.pageMarkdown.put("archive-page", "Historical notes");
        apiClient.addChild("projects-page", "todo-page", "Todo");
        apiClient.addChild("archive-page", "logs-page", "Logs");
        apiClient.pageMarkdown.put("todo-page", "# Deploy\n\nDeployment checklist for production release.");
        apiClient.pageMarkdown.put("logs-page", "# Deploy\n\nOld deployment notes from last quarter.");

        NotionReindexSummary summary = service.reindexAll();

        assertEquals(5, summary.pagesIndexed());
        List<NotionSearchHit> filtered = service.search("deployment release", "Projects", 10);
        assertEquals(1, filtered.size());
        assertEquals("Projects/Todo", filtered.getFirst().path());
        assertEquals("Todo", filtered.getFirst().title());
        assertTrue(filtered.getFirst().snippet().toLowerCase(Locale.ROOT).contains("deployment"));
        assertEquals("Deploy", filtered.getFirst().headingPath());

        List<NotionSearchHit> unfiltered = service.search("deployment", "", 10);
        assertEquals(2, unfiltered.size());
        assertEquals("Projects/Todo", unfiltered.getFirst().path());
        assertTrue(unfiltered.stream().anyMatch(hit -> "Archive/Logs".equals(hit.path())));
    }

    @Test
    void shouldReturnEmptyResultsForBlankQuery() {
        apiClient.rootTitle = "Workspace";
        apiClient.pageMarkdown.put("root-page", "Workspace overview");

        service.reindexAll();
        List<NotionSearchHit> results = service.search("   ", "", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldOverwritePreviousChunksWhenReindexingAgain() {
        apiClient.rootTitle = "Workspace";
        apiClient.pageMarkdown.put("root-page", "Workspace overview");
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.pageMarkdown.put("projects-page", "alpha beta");

        service.reindexAll();
        assertFalse(service.search("gamma", "", 5).stream().findAny().isPresent());

        apiClient.pageMarkdown.put("projects-page", "beta gamma");
        service.reindexAll();

        assertEquals(1, service.search("gamma", "", 5).size());
        assertEquals(0, service.search("alpha", "", 5).size());
    }

    private static final class StubNotionApiClient extends NotionApiClient {

        private String rootTitle = "";
        private final Map<String, List<NotionPageSummary>> childrenByParent = new LinkedHashMap<>();
        private final Map<String, String> pageMarkdown = new LinkedHashMap<>();

        private StubNotionApiClient(NotionPluginConfigService configService) {
            super(configService);
        }

        @Override
        public String retrievePageTitle(String pageId) {
            return "root-page".equals(pageId) ? rootTitle : "";
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
