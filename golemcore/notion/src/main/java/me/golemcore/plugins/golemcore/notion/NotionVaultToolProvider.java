package me.golemcore.plugins.golemcore.notion;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class NotionVaultToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_TARGET_PATH = "target_path";
    private static final String PARAM_NEW_NAME = "new_name";

    private final NotionVaultService service;

    public NotionVaultToolProvider(NotionVaultService service) {
        this.service = service;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("notion_vault")
                .description("Use Notion pages as a vault through the official Notion HTTP API.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of(
                                                "list_directory",
                                                "search_notes",
                                                "read_note",
                                                "create_note",
                                                "update_note",
                                                "delete_note",
                                                "move_note",
                                                "rename_note")),
                                PARAM_QUERY, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Full-text query against the local Notion index."),
                                PARAM_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Pseudo-path to a Notion page or subtree."),
                                PARAM_LIMIT, Map.of(
                                        TYPE, "integer",
                                        "description", "Maximum number of search results to return."),
                                PARAM_CONTENT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Markdown content for create_note or update_note."),
                                PARAM_TARGET_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Target pseudo-path for move_note."),
                                PARAM_NEW_NAME, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "New leaf page title for rename_note.")),
                        REQUIRED, List.of(PARAM_OPERATION),
                        "allOf", List.of(
                                requiredWhen("search_notes", List.of(PARAM_QUERY)),
                                requiredWhen("read_note", List.of(PARAM_PATH)),
                                requiredWhen("create_note", List.of(PARAM_PATH, PARAM_CONTENT)),
                                requiredWhen("update_note", List.of(PARAM_PATH, PARAM_CONTENT)),
                                requiredWhen("delete_note", List.of(PARAM_PATH)),
                                requiredWhen("move_note", List.of(PARAM_PATH, PARAM_TARGET_PATH)),
                                requiredWhen("rename_note", List.of(PARAM_PATH, PARAM_NEW_NAME)))))
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
        case "search_notes" -> service.searchNotes(
                readString(parameters.get(PARAM_QUERY)),
                readString(parameters.get(PARAM_PATH)),
                readInteger(parameters.get(PARAM_LIMIT)));
        case "read_note" -> service.readNote(readString(parameters.get(PARAM_PATH)));
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
                "Unsupported notion_vault operation: " + operation);
        };
    }

    private Map<String, Object> requiredWhen(String operation, List<String> requiredFields) {
        return Map.of(
                "if", Map.of(
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of("const", operation))),
                "then", Map.of(
                        REQUIRED, requiredFields));
    }

    private String readString(Object value) {
        if (value instanceof String text) {
            return text;
        }
        return null;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
