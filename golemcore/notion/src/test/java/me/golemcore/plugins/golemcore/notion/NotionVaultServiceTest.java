package me.golemcore.plugins.golemcore.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.notion.support.NotionApiClient;
import me.golemcore.plugins.golemcore.notion.support.NotionLocalIndexService;
import me.golemcore.plugins.golemcore.notion.support.NotionSearchHit;
import me.golemcore.plugins.golemcore.notion.support.NotionPageSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionRagSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;

class NotionVaultServiceTest {

    private NotionPluginConfigService configService;
    private StubNotionApiClient apiClient;
    private NotionLocalIndexService localIndexService;
    private NotionRagSyncService ragSyncService;
    private NotionVaultService service;

    @BeforeEach
    void setUp() {
        configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(config(true, true, true, true, 12_000));
        apiClient = new StubNotionApiClient(configService);
        localIndexService = mock(NotionLocalIndexService.class);
        ragSyncService = mock(NotionRagSyncService.class);
        service = new NotionVaultService(apiClient, configService, localIndexService, ragSyncService);
    }

    @Test
    void shouldListRootChildrenUsingPseudoPaths() {
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.addChild("root-page", "inbox-page", "Inbox");

        ToolResult result = service.listDirectory("");

        assertTrue(result.isSuccess());
        assertEquals(List.of("root-page"), apiClient.listChildCalls);
        assertEquals("Listed 2 item(s) in /", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("", data.get("path"));
        assertEquals(List.of("Projects", "Inbox"), data.get("entries"));
    }

    @Test
    void shouldReadRootPageWhenPathIsBlankAndRespectMaxChars() {
        when(configService.getConfig()).thenReturn(config(true, true, true, true, 5));
        apiClient.pageMarkdown.put("root-page", "123456789");

        ToolResult result = service.readNote("");

        assertTrue(result.isSuccess());
        assertEquals(List.of("root-page"), apiClient.readMarkdownCalls);
        assertEquals("12345", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("", data.get("path"));
        assertEquals(true, data.get("truncated"));
        assertEquals(9, data.get("originalLength"));
    }

    @Test
    void shouldSearchNotesUsingLocalIndex() {
        when(configService.getConfig()).thenReturn(config(true, true, true, true, 12_000, true));
        when(localIndexService.search("deployment", "Projects", 3)).thenReturn(List.of(
                new NotionSearchHit("chunk-1", "todo-page", "Projects/Todo", "Todo",
                        "deployment checklist ...", "Deploy")));

        ToolResult result = service.searchNotes("deployment", "Projects", 3);

        assertTrue(result.isSuccess());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("deployment", data.get("query"));
        assertEquals("Projects", data.get("path"));
        assertEquals(1, data.get("count"));
        assertEquals(List.of(Map.of(
                "chunk_id", "chunk-1",
                "page_id", "todo-page",
                "path", "Projects/Todo",
                "title", "Todo",
                "heading_path", "Deploy",
                "snippet", "deployment checklist ...")), data.get("results"));
    }

    @Test
    void shouldRejectSearchWhenLocalIndexDisabled() {
        ToolResult result = service.searchNotes("deployment", null, 5);

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("local index"));
    }

    @Test
    void shouldCreateChildPageUsingParentPseudoPathAndLeafTitle() {
        apiClient.addChild("root-page", "projects-page", "Projects");

        ToolResult result = service.createNote("Projects/Todo", "# Todo\n\nBody");

        assertTrue(result.isSuccess());
        assertEquals(List.of(new CreateCall("projects-page", "Todo", "# Todo\n\nBody")), apiClient.createCalls);
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("Projects/Todo", data.get("path"));
        assertEquals("todo-page", data.get("page_id"));
        verify(ragSyncService).upsertDocument(
                "todo-page",
                "Projects/Todo",
                "Todo",
                "# Todo\n\nBody",
                "https://notion.so/todo-page");
    }

    @Test
    void shouldArchiveResolvedPageWhenDeleteEnabled() {
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.addChild("projects-page", "todo-page", "Todo");

        ToolResult result = service.deleteNote("Projects/Todo");

        assertTrue(result.isSuccess());
        assertEquals(List.of("root-page", "projects-page"), apiClient.listChildCalls);
        assertEquals(List.of("todo-page"), apiClient.archiveCalls);
        verify(ragSyncService).deleteDocument("todo-page");
    }

    @Test
    void shouldMoveAndRenamePageWhenTargetPathChangesParentAndLeaf() {
        apiClient.addChild("root-page", "projects-page", "Projects");
        apiClient.addChild("root-page", "archive-page", "Archive");
        apiClient.addChild("projects-page", "todo-page", "Todo");
        apiClient.pageMarkdown.put("todo-page", "# Todo");

        ToolResult result = service.moveNote("Projects/Todo", "Archive/Done");

        assertTrue(result.isSuccess());
        assertEquals(List.of(new MoveCall("todo-page", "archive-page")), apiClient.moveCalls);
        assertEquals(List.of(new RenameCall("todo-page", "Done")), apiClient.renameCalls);
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("Projects/Todo", data.get("path"));
        assertEquals("Archive/Done", data.get("target_path"));
        verify(ragSyncService).upsertDocument(
                "todo-page",
                "Archive/Done",
                "Done",
                "# Todo",
                "https://notion.so/todo-page");
    }

    @Test
    void shouldRejectDeletingConfiguredRootPage() {
        ToolResult result = service.deleteNote("");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("root page"));
        assertTrue(apiClient.archiveCalls.isEmpty());
    }

    private NotionPluginConfig config(
            boolean allowWrite,
            boolean allowDelete,
            boolean allowMove,
            boolean allowRename,
            int maxReadChars) {
        return config(allowWrite, allowDelete, allowMove, allowRename, maxReadChars, false);
    }

    private NotionPluginConfig config(
            boolean allowWrite,
            boolean allowDelete,
            boolean allowMove,
            boolean allowRename,
            int maxReadChars,
            boolean localIndexEnabled) {
        return NotionPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://api.notion.com")
                .apiVersion("2026-03-11")
                .apiKey("secret")
                .rootPageId("root-page")
                .timeoutMs(30_000)
                .maxReadChars(maxReadChars)
                .allowWrite(allowWrite)
                .allowDelete(allowDelete)
                .allowMove(allowMove)
                .allowRename(allowRename)
                .localIndexEnabled(localIndexEnabled)
                .reindexSchedulePreset("disabled")
                .reindexCronExpression("")
                .ragSyncEnabled(false)
                .targetRagProviderId("")
                .ragCorpusId("notion")
                .build();
    }

    private static final class StubNotionApiClient extends NotionApiClient {

        private final Map<String, List<NotionPageSummary>> childrenByParent = new LinkedHashMap<>();
        private final Map<String, String> pageMarkdown = new LinkedHashMap<>();
        private final Map<String, String> pageTitles = new LinkedHashMap<>();
        private final List<String> listChildCalls = new ArrayList<>();
        private final List<String> readMarkdownCalls = new ArrayList<>();
        private final List<CreateCall> createCalls = new ArrayList<>();
        private final List<String> archiveCalls = new ArrayList<>();
        private final List<MoveCall> moveCalls = new ArrayList<>();
        private final List<RenameCall> renameCalls = new ArrayList<>();

        private StubNotionApiClient(NotionPluginConfigService configService) {
            super(configService);
            pageTitles.put("root-page", "Root");
        }

        @Override
        public String retrievePageTitle(String pageId) {
            return pageTitles.getOrDefault(pageId, "");
        }

        @Override
        public List<NotionPageSummary> listChildPages(String parentPageId) {
            listChildCalls.add(parentPageId);
            return childrenByParent.getOrDefault(parentPageId, List.of());
        }

        @Override
        public String retrievePageMarkdown(String pageId) {
            readMarkdownCalls.add(pageId);
            return pageMarkdown.getOrDefault(pageId, "");
        }

        @Override
        public NotionPageSummary createChildPage(String parentPageId, String title, String markdown) {
            createCalls.add(new CreateCall(parentPageId, title, markdown));
            return new NotionPageSummary("todo-page", title, "https://notion.so/todo-page");
        }

        @Override
        public void archivePage(String pageId) {
            archiveCalls.add(pageId);
        }

        @Override
        public void movePage(String pageId, String targetParentPageId) {
            moveCalls.add(new MoveCall(pageId, targetParentPageId));
        }

        @Override
        public void renamePage(String pageId, String title) {
            renameCalls.add(new RenameCall(pageId, title));
        }

        private void addChild(String parentPageId, String pageId, String title) {
            childrenByParent.computeIfAbsent(parentPageId, ignored -> new ArrayList<>())
                    .add(new NotionPageSummary(pageId, title, "https://notion.so/" + pageId));
        }
    }

    private record CreateCall(String parentPageId, String title, String markdown) {
    }

    private record MoveCall(String pageId, String targetParentPageId) {
    }

    private record RenameCall(String pageId, String title) {
    }
}
