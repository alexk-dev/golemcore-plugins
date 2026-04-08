package me.golemcore.plugins.golemcore.nextcloud;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class NextcloudFilesToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_CONTENT_BASE64 = "content_base64";
    private static final String PARAM_APPEND = "append";
    private static final String PARAM_TARGET_PATH = "target_path";

    private final NextcloudFilesService service;

    public NextcloudFilesToolProvider(NextcloudFilesService service) {
        this.service = service;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("nextcloud_files")
                .description("Work with Nextcloud files through the standard WebDAV layer.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of(
                                                "list_directory",
                                                "read_file",
                                                "write_file",
                                                "create_directory",
                                                "delete",
                                                "move",
                                                "copy",
                                                "file_info")),
                                PARAM_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Path relative to the configured Nextcloud root."),
                                PARAM_CONTENT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "UTF-8 text content for write_file."),
                                PARAM_CONTENT_BASE64, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Base64-encoded bytes for write_file."),
                                PARAM_APPEND, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Append to an existing file when writing."),
                                PARAM_TARGET_PATH, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Target path for move or copy.")),
                        REQUIRED, List.of(PARAM_OPERATION),
                        "allOf", List.of(
                                requiredWhen("read_file", List.of(PARAM_PATH)),
                                requiredWhen("write_file", List.of(PARAM_PATH)),
                                requiredWhen("create_directory", List.of(PARAM_PATH)),
                                requiredWhen("delete", List.of(PARAM_PATH)),
                                requiredWhen("move", List.of(PARAM_PATH, PARAM_TARGET_PATH)),
                                requiredWhen("copy", List.of(PARAM_PATH, PARAM_TARGET_PATH)),
                                requiredWhen("file_info", List.of(PARAM_PATH)))))
                .build();
    }

    @Override
    public boolean isEnabled() {
        NextcloudPluginConfig config = service.getConfig();
        return Boolean.TRUE.equals(config.getEnabled())
                && hasText(config.getUsername())
                && hasText(config.getAppPassword());
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
        case "read_file" -> service.readFile(readString(parameters.get(PARAM_PATH)));
        case "write_file" -> service.writeFile(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_CONTENT)),
                readString(parameters.get(PARAM_CONTENT_BASE64)),
                readBoolean(parameters.get(PARAM_APPEND)));
        case "create_directory" -> service.createDirectory(readString(parameters.get(PARAM_PATH)));
        case "delete" -> service.delete(readString(parameters.get(PARAM_PATH)));
        case "move" -> service.move(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_TARGET_PATH)));
        case "copy" -> service.copy(
                readString(parameters.get(PARAM_PATH)),
                readString(parameters.get(PARAM_TARGET_PATH)));
        case "file_info" -> service.fileInfo(readString(parameters.get(PARAM_PATH)));
        default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "Unsupported nextcloud_files operation: " + operation);
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

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
