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
    void shouldRequirePathForReadNoteInSchema() {
        ToolDefinition definition = provider.getDefinition();

        Map<String, Object> schema = definition.getInputSchema();
        List<?> allOf = (List<?>) schema.get("allOf");
        Map<?, ?> readRule = (Map<?, ?>) allOf.stream()
                .map(Map.class::cast)
                .filter(rule -> {
                    Map<?, ?> condition = (Map<?, ?>) rule.get("if");
                    Map<?, ?> conditionProperties = (Map<?, ?>) condition.get("properties");
                    Map<?, ?> operationProperty = (Map<?, ?>) conditionProperties.get("operation");
                    return "read_note".equals(operationProperty.get("const"));
                })
                .findFirst()
                .orElseThrow();
        Map<?, ?> thenClause = (Map<?, ?>) readRule.get("then");

        assertEquals(List.of("path"), thenClause.get("required"));
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
    void shouldDispatchReadNoteToVaultService() {
        when(service.readNote("Projects/Todo.md"))
                .thenReturn(ToolResult.success("read"));

        ToolResult result = provider.execute(Map.of(
                "operation", "read_note",
                "path", "Projects/Todo.md")).join();

        assertTrue(result.isSuccess());
        verify(service).readNote("Projects/Todo.md");
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
    void shouldRejectNonIntegerContextLength() {
        ToolResult result = provider.execute(Map.of(
                "operation", "search_notes",
                "query", "daily review",
                "context_length", 42.5d)).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("context_length"));
    }

    @Test
    void shouldRejectStringContextLength() {
        ToolResult result = provider.execute(Map.of(
                "operation", "search_notes",
                "query", "daily review",
                "context_length", "42")).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("context_length"));
    }

    @Test
    void shouldDispatchListDirectoryWithoutPath() {
        when(service.listDirectory(null))
                .thenReturn(ToolResult.success("listed"));

        ToolResult result = provider.execute(Map.of("operation", "list_directory")).join();

        assertTrue(result.isSuccess());
        verify(service).listDirectory(null);
    }

    @Test
    void shouldDispatchCreateToVaultService() {
        when(service.createNote("Projects/Todo.md", "# Todo"))
                .thenReturn(ToolResult.success("created"));

        ToolResult result = provider.execute(Map.of(
                "operation", "create_note",
                "path", "Projects/Todo.md",
                "content", "# Todo")).join();

        assertTrue(result.isSuccess());
        verify(service).createNote("Projects/Todo.md", "# Todo");
    }

    @Test
    void shouldDispatchUpdateToVaultService() {
        when(service.updateNote("Projects/Todo.md", "# Updated"))
                .thenReturn(ToolResult.success("updated"));

        ToolResult result = provider.execute(Map.of(
                "operation", "update_note",
                "path", "Projects/Todo.md",
                "content", "# Updated")).join();

        assertTrue(result.isSuccess());
        verify(service).updateNote("Projects/Todo.md", "# Updated");
    }

    @Test
    void shouldDispatchDeleteToVaultService() {
        when(service.deleteNote("Projects/Todo.md"))
                .thenReturn(ToolResult.success("deleted"));

        ToolResult result = provider.execute(Map.of(
                "operation", "delete_note",
                "path", "Projects/Todo.md")).join();

        assertTrue(result.isSuccess());
        verify(service).deleteNote("Projects/Todo.md");
    }

    @Test
    void shouldDispatchMoveToVaultService() {
        when(service.moveNote("Projects/Todo.md", "Archive/Todo.md"))
                .thenReturn(ToolResult.success("moved"));

        ToolResult result = provider.execute(Map.of(
                "operation", "move_note",
                "path", "Projects/Todo.md",
                "target_path", "Archive/Todo.md")).join();

        assertTrue(result.isSuccess());
        verify(service).moveNote("Projects/Todo.md", "Archive/Todo.md");
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.EXECUTION_FAILED, result.getFailureKind());
        assertTrue(result.getError().contains("Unsupported"));
    }
}
