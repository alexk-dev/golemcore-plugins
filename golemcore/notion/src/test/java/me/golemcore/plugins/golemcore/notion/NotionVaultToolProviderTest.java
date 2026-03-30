package me.golemcore.plugins.golemcore.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotionVaultToolProviderTest {

    private NotionVaultService service;
    private NotionVaultToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(NotionVaultService.class);
        provider = new NotionVaultToolProvider(service);
    }

    @Test
    void shouldExposeSupportedVaultOperations() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> operation = (Map<?, ?>) properties.get("operation");

        assertEquals("notion_vault", definition.getName());
        assertEquals(List.of(
                "list_directory",
                "search_notes",
                "read_note",
                "create_note",
                "update_note",
                "delete_note",
                "move_note",
                "rename_note"), operation.get("enum"));
    }

    @Test
    void shouldRejectMissingOperation() {
        ToolResult result = provider.execute(Map.of()).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("operation is required"));
    }

    @Test
    void shouldDispatchListDirectoryWithoutPath() {
        when(service.listDirectory(null)).thenReturn(ToolResult.success("listed"));

        ToolResult result = provider.execute(Map.of("operation", "list_directory")).join();

        assertTrue(result.isSuccess());
        verify(service).listDirectory(null);
    }

    @Test
    void shouldDispatchReadNoteToVaultService() {
        when(service.readNote("Projects/Todo")).thenReturn(ToolResult.success("read"));

        ToolResult result = provider.execute(Map.of(
                "operation", "read_note",
                "path", "Projects/Todo")).join();

        assertTrue(result.isSuccess());
        verify(service).readNote("Projects/Todo");
    }

    @Test
    void shouldDispatchSearchNotesToVaultService() {
        when(service.searchNotes("deployment", "Projects", 3)).thenReturn(ToolResult.success("searched"));

        ToolResult result = provider.execute(Map.of(
                "operation", "search_notes",
                "query", "deployment",
                "path", "Projects",
                "limit", 3)).join();

        assertTrue(result.isSuccess());
        verify(service).searchNotes("deployment", "Projects", 3);
    }

    @Test
    void shouldDispatchCreateToVaultService() {
        when(service.createNote("Projects/Todo", "# Todo")).thenReturn(ToolResult.success("created"));

        ToolResult result = provider.execute(Map.of(
                "operation", "create_note",
                "path", "Projects/Todo",
                "content", "# Todo")).join();

        assertTrue(result.isSuccess());
        verify(service).createNote("Projects/Todo", "# Todo");
    }

    @Test
    void shouldDispatchUpdateToVaultService() {
        when(service.updateNote("Projects/Todo", "# Updated")).thenReturn(ToolResult.success("updated"));

        ToolResult result = provider.execute(Map.of(
                "operation", "update_note",
                "path", "Projects/Todo",
                "content", "# Updated")).join();

        assertTrue(result.isSuccess());
        verify(service).updateNote("Projects/Todo", "# Updated");
    }

    @Test
    void shouldDispatchDeleteToVaultService() {
        when(service.deleteNote("Projects/Todo")).thenReturn(ToolResult.success("deleted"));

        ToolResult result = provider.execute(Map.of(
                "operation", "delete_note",
                "path", "Projects/Todo")).join();

        assertTrue(result.isSuccess());
        verify(service).deleteNote("Projects/Todo");
    }

    @Test
    void shouldDispatchMoveToVaultService() {
        when(service.moveNote("Projects/Todo", "Archive/Done")).thenReturn(ToolResult.success("moved"));

        ToolResult result = provider.execute(Map.of(
                "operation", "move_note",
                "path", "Projects/Todo",
                "target_path", "Archive/Done")).join();

        assertTrue(result.isSuccess());
        verify(service).moveNote("Projects/Todo", "Archive/Done");
    }

    @Test
    void shouldDispatchRenameToVaultService() {
        when(service.renameNote("Projects/Todo", "Done")).thenReturn(ToolResult.success("renamed"));

        ToolResult result = provider.execute(Map.of(
                "operation", "rename_note",
                "path", "Projects/Todo",
                "new_name", "Done")).join();

        assertTrue(result.isSuccess());
        verify(service).renameNote("Projects/Todo", "Done");
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("Unsupported"));
    }
}
