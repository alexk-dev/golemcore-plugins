package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsidianVaultToolProviderTest {

    private ObsidianVaultService service;
    private ObsidianVaultToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(ObsidianVaultService.class);
        provider = new ObsidianVaultToolProvider(service);
    }

    @Test
    void shouldExposeAllSupportedOperations() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> operation = (Map<?, ?>) properties.get("operation");

        assertEquals("obsidian_vault", definition.getName());
        assertEquals(List.of(
                "list_directory",
                "read_note",
                "search_notes",
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
    void shouldDispatchRenameToVaultService() {
        when(service.renameNote("Projects/Todo.md", "Done.md"))
                .thenReturn(ToolResult.success("renamed"));

        ToolResult result = provider.execute(Map.of(
                "operation", "rename_note",
                "path", "Projects/Todo.md",
                "new_name", "Done.md")).join();

        assertTrue(result.isSuccess());
        verify(service).renameNote("Projects/Todo.md", "Done.md");
    }

    @Test
    void shouldDispatchSearchWithOptionalContextLength() {
        when(service.searchNotes("daily review", 42))
                .thenReturn(ToolResult.success("found"));

        ToolResult result = provider.execute(Map.of(
                "operation", "search_notes",
                "query", "daily review",
                "context_length", 42)).join();

        assertTrue(result.isSuccess());
        verify(service).searchNotes("daily review", 42);
    }

    @Test
    void shouldDispatchListDirectoryWithoutPath() {
        when(service.listDirectory(null))
                .thenReturn(ToolResult.success("listed"));

        ToolResult result = provider.execute(Map.of("operation", "list_directory")).join();

        assertTrue(result.isSuccess());
        verify(service).listDirectory(null);
    }
}
