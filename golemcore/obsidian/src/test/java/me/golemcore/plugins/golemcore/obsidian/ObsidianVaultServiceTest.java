package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.obsidian.model.ObsidianSearchResult;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianApiClient;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsidianVaultServiceTest {

    private ObsidianPluginConfigService configService;
    private StubObsidianApiClient apiClient;
    private ObsidianVaultService service;

    @BeforeEach
    void setUp() {
        configService = mock(ObsidianPluginConfigService.class);
        when(configService.getConfig()).thenReturn(config(true, true, true, true, 12_000));
        apiClient = new StubObsidianApiClient(configService);
        service = new ObsidianVaultService(apiClient, configService);
    }

    @Test
    void shouldListVaultRootDirectory() {
        apiClient.listDirectoryResults.put("", List.of("Inbox.md", "Projects/"));

        ToolResult result = service.listDirectory("");

        assertTrue(result.isSuccess());
        assertEquals(List.of(""), apiClient.listDirectoryCalls);
        assertEquals("Listed 2 item(s) in /", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("", data.get("path"));
        assertEquals(List.of("Inbox.md", "Projects/"), data.get("entries"));
    }

    @Test
    void shouldNormalizeNotePathsBeforeUpdating() {
        apiClient.noteContents.put("Inbox.md", "# Existing");

        ToolResult result = service.updateNote("./Projects/../Inbox.md", "# Inbox");

        assertTrue(result.isSuccess());
        assertEquals(List.of("Inbox.md"), apiClient.readPaths);
        assertEquals(List.of("Inbox.md"), apiClient.writePaths);
        assertEquals(List.of("# Inbox"), apiClient.writeContents);
    }

    @Test
    void shouldRejectBlankNotePathBeforeCallingApi() {
        ToolResult result = service.readNote("   ");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("Path is required"));
        assertTrue(apiClient.readPaths.isEmpty());
    }

    @Test
    void shouldRejectNotePathsWithoutMarkdownExtensionBeforeCallingApi() {
        ToolResult result = service.createNote("Projects/Todo.txt", "# Todo");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains(".md"));
        assertTrue(apiClient.writePaths.isEmpty());
    }

    @Test
    void shouldRejectDirectoryListingPathsThatPointToNotes() {
        ToolResult result = service.listDirectory("Inbox.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("directory"));
        assertTrue(apiClient.listDirectoryCalls.isEmpty());
    }

    @Test
    void shouldDenyCreateWhenWriteIsDisabled() {
        when(configService.getConfig()).thenReturn(config(false, true, true, true, 12_000));

        ToolResult result = service.createNote("Projects/Todo.md", "# Todo");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("write is disabled"));
        assertTrue(apiClient.writePaths.isEmpty());
    }

    @Test
    void shouldRejectCreateWhenDestinationAlreadyExists() {
        apiClient.noteContents.put("Projects/Todo.md", "# Existing");

        ToolResult result = service.createNote("Projects/Todo.md", "# Todo");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("already exists"));
        assertTrue(apiClient.writePaths.isEmpty());
    }

    @Test
    void shouldDenyDeleteWhenDeleteIsDisabled() {
        when(configService.getConfig()).thenReturn(config(true, false, true, true, 12_000));

        ToolResult result = service.deleteNote("Projects/Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("delete is disabled"));
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldRejectDeleteWhenSourceDoesNotExist() {
        ToolResult result = service.deleteNote("Projects/Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("does not exist"));
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldDenyMoveWhenMoveIsDisabled() {
        when(configService.getConfig()).thenReturn(config(true, true, false, true, 12_000));

        ToolResult result = service.moveNote("Projects/Todo.md", "Archive/Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("move is disabled"));
        assertTrue(apiClient.readPaths.isEmpty());
        assertTrue(apiClient.writePaths.isEmpty());
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldDenyRenameWhenRenameIsDisabled() {
        when(configService.getConfig()).thenReturn(config(true, true, true, false, 12_000));

        ToolResult result = service.renameNote("Projects/Todo.md", "Done.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertTrue(result.getError().contains("rename is disabled"));
        assertTrue(apiClient.readPaths.isEmpty());
        assertTrue(apiClient.writePaths.isEmpty());
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldRejectUpdateWhenSourceDoesNotExist() {
        ToolResult result = service.updateNote("Projects/Todo.md", "# Todo");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("does not exist"));
        assertTrue(apiClient.writePaths.isEmpty());
    }

    @Test
    void shouldRejectRenameWhenNewNameContainsPathSeparators() {
        ToolResult result = service.renameNote("Projects/Todo.md", "Archive/Done.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("newName"));
        assertTrue(apiClient.readPaths.isEmpty());
    }

    @Test
    void shouldTruncateReadNoteContentAndExposeStructuredFlag() {
        when(configService.getConfig()).thenReturn(config(true, true, true, true, 5));
        apiClient.noteContents.put("Inbox.md", "123456789");

        ToolResult result = service.readNote("Inbox.md");

        assertTrue(result.isSuccess());
        assertEquals("12345", result.getOutput());
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("Inbox.md", data.get("path"));
        assertEquals("12345", data.get("content"));
        assertEquals(true, data.get("truncated"));
        assertEquals(9, data.get("originalLength"));
    }

    @Test
    void shouldSearchNotesUsingRequestedContextLength() {
        apiClient.searchResults = List.of(new ObsidianSearchResult(
                "Inbox.md",
                0.91d,
                List.of(new ObsidianSearchResult.Match(
                        "Daily review notes",
                        new ObsidianSearchResult.MatchSpan(6, 12, "content")))));

        ToolResult result = service.searchNotes("daily review", 42);

        assertTrue(result.isSuccess());
        assertEquals(List.of("daily review"), apiClient.searchQueries);
        assertEquals(List.of(42), apiClient.searchContextLengths);
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals(1, data.get("count"));
        assertEquals(apiClient.searchResults, data.get("results"));
    }

    @Test
    void shouldDeleteValidatedNote() {
        apiClient.noteContents.put("Todo.md", "# Todo");

        ToolResult result = service.deleteNote("./Projects/../Todo.md");

        assertTrue(result.isSuccess());
        assertEquals(List.of("Todo.md"), apiClient.readPaths);
        assertEquals(List.of("Todo.md"), apiClient.deletePaths);
    }

    @Test
    void shouldRejectMoveWhenTargetAlreadyExists() {
        apiClient.noteContents.put("Projects/Todo.md", "# Todo");
        apiClient.noteContents.put("Archive/Todo.md", "# Existing");

        ToolResult result = service.moveNote("Projects/Todo.md", "Archive/Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("already exists"));
        assertEquals(List.of("Projects/Todo.md", "Archive/Todo.md"), apiClient.readPaths);
        assertTrue(apiClient.writePaths.isEmpty());
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldRejectMoveWhenSourceAndTargetNormalizeToSamePath() {
        apiClient.noteContents.put("Inbox.md", "# Inbox");

        ToolResult result = service.moveNote("./Inbox.md", "Inbox.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("must differ"));
        assertTrue(apiClient.writePaths.isEmpty());
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    @Test
    void shouldSurfacePartialFailureWhenMoveCannotDeleteSource() {
        apiClient.noteContents.put("Projects/Todo.md", "# Todo");
        apiClient.deleteFailures.put("Projects/Todo.md", new ObsidianApiException(500, "delete failed"));

        ToolResult result = service.moveNote("Projects/Todo.md", "Archive/Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("both source and target may now exist"));
        assertEquals(List.of("Projects/Todo.md", "Archive/Todo.md"), apiClient.readPaths);
        assertEquals(List.of("Archive/Todo.md"), apiClient.writePaths);
        assertEquals(List.of("Projects/Todo.md"), apiClient.deletePaths);
        Map<?, ?> data = assertInstanceOf(Map.class, result.getData());
        assertEquals("Projects/Todo.md", data.get("path"));
        assertEquals("Archive/Todo.md", data.get("target_path"));
    }

    @Test
    void shouldRenameByWritingSiblingTargetAndDeletingSource() {
        apiClient.noteContents.put("Projects/Todo.md", "# Todo");

        ToolResult result = service.renameNote("Projects/Todo.md", "Done.md");

        assertTrue(result.isSuccess());
        assertEquals(List.of("Projects/Todo.md", "Projects/Done.md"), apiClient.readPaths);
        assertEquals(List.of("Projects/Done.md"), apiClient.writePaths);
        assertEquals(List.of("Projects/Todo.md"), apiClient.deletePaths);
    }

    @Test
    void shouldRejectRenameWhenNewNameKeepsSamePath() {
        apiClient.noteContents.put("Projects/Todo.md", "# Todo");

        ToolResult result = service.renameNote("Projects/Todo.md", "Todo.md");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("must differ"));
        assertTrue(apiClient.writePaths.isEmpty());
        assertTrue(apiClient.deletePaths.isEmpty());
    }

    private ObsidianPluginConfig config(
            boolean allowWrite,
            boolean allowDelete,
            boolean allowMove,
            boolean allowRename,
            int maxReadChars) {
        return ObsidianPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://127.0.0.1:27124")
                .apiKey("api-key")
                .timeoutMs(30_000)
                .allowInsecureTls(false)
                .defaultSearchContextLength(100)
                .maxReadChars(maxReadChars)
                .allowWrite(allowWrite)
                .allowDelete(allowDelete)
                .allowMove(allowMove)
                .allowRename(allowRename)
                .build();
    }

    private static final class StubObsidianApiClient extends ObsidianApiClient {

        private final Map<String, List<String>> listDirectoryResults = new HashMap<>();
        private final Map<String, String> noteContents = new HashMap<>();
        private final Map<String, RuntimeException> deleteFailures = new HashMap<>();
        private final List<String> listDirectoryCalls = new ArrayList<>();
        private final List<String> readPaths = new ArrayList<>();
        private final List<String> writePaths = new ArrayList<>();
        private final List<String> writeContents = new ArrayList<>();
        private final List<String> deletePaths = new ArrayList<>();
        private final List<String> searchQueries = new ArrayList<>();
        private final List<Integer> searchContextLengths = new ArrayList<>();
        private List<ObsidianSearchResult> searchResults = List.of();

        private StubObsidianApiClient(ObsidianPluginConfigService configService) {
            super(configService);
        }

        @Override
        public List<String> listDirectory(String path) {
            listDirectoryCalls.add(path);
            return listDirectoryResults.getOrDefault(path, List.of());
        }

        @Override
        public String readNote(String path) {
            readPaths.add(path);
            if (!noteContents.containsKey(path)) {
                throw new ObsidianApiException(404, "missing note");
            }
            return noteContents.get(path);
        }

        @Override
        public void writeNote(String path, String content) {
            writePaths.add(path);
            writeContents.add(content);
            noteContents.put(path, content);
        }

        @Override
        public void deleteNote(String path) {
            deletePaths.add(path);
            RuntimeException failure = deleteFailures.get(path);
            if (failure != null) {
                throw failure;
            }
            noteContents.remove(path);
        }

        @Override
        public List<ObsidianSearchResult> simpleSearch(String query, int contextLength) {
            searchQueries.add(query);
            searchContextLengths.add(contextLength);
            return searchResults;
        }
    }
}
