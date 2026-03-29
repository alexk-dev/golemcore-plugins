package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class ObsidianVaultToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_CONTEXT_LENGTH = "context_length";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_TARGET_PATH = "target_path";
    private static final String PARAM_NEW_NAME = "new_name";

    private final ObsidianVaultService service;

    public ObsidianVaultToolProvider(ObsidianVaultService service) {
        this.service = service;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("obsidian_vault")
                .description("Use an Obsidian vault through obsidian-local-rest-api.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of(
                                                "list_directory",
                                                "read_note",
                                                "search_notes",
                                                "create_note",
                                                "update_note",
                                                "delete_note",
                                                "move_note",
                                                "rename_note")),
                                PARAM_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Vault-relative note or directory path."),
                                PARAM_QUERY, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Search query for search_notes."),
                                PARAM_CONTEXT_LENGTH, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional context length for search_notes."),
                                PARAM_CONTENT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Markdown content for create_note or update_note."),
                                PARAM_TARGET_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Target vault-relative path for move_note."),
                                PARAM_NEW_NAME, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "New file name for rename_note.")),
                        REQUIRED, List.of(PARAM_OPERATION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeOperation(parameters));
    }

    private ToolResult executeOperation(Map<String, Object> parameters) {
        String operation = readString(parameters.get(PARAM_OPERATION));
        if (operation == null || operation.isBlank()) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "operation is required");
        }

        return switch (operation) {
        case "list_directory" -> service.listDirectory(readString(parameters.get(PARAM_PATH)));
        case "read_note" -> service.readNote(readString(parameters.get(PARAM_PATH)));
        case "search_notes" -> service.searchNotes(
                readString(parameters.get(PARAM_QUERY)),
                readInteger(parameters.get(PARAM_CONTEXT_LENGTH)));
        case "create_note" -> service.createNote(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_CONTENT)));
        case "update_note" -> service.updateNote(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_CONTENT)));
        case "delete_note" -> service.deleteNote(readString(parameters.get(PARAM_PATH)));
        case "move_note" -> service.moveNote(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_TARGET_PATH)));
        case "rename_note" -> service.renameNote(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_NEW_NAME)));
        default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "Unsupported obsidian_vault operation: " + operation);
        };
    }

    private String readString(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
