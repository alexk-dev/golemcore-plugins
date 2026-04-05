package me.golemcore.plugins.golemcore.notion;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class NotionVaultToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_CONTENT_JSON = "content_json";
    private static final String PARAM_TARGET_PATH = "target_path";
    private static final String PARAM_NEW_NAME = "new_name";
    private static final String PARAM_PARENT_PATH = "parent_path";
    private static final String PARAM_TITLE = "title";
    private static final String PARAM_DATABASE_ID = "database_id";
    private static final String PARAM_DATA_SOURCE_ID = "data_source_id";
    private static final String PARAM_PAGE_ID = "page_id";
    private static final String PARAM_DESCRIPTION = "description";
    private static final String PARAM_PROPERTIES_JSON = "properties_json";
    private static final String PARAM_FILTER_JSON = "filter_json";
    private static final String PARAM_SORTS_JSON = "sorts_json";
    private static final String PARAM_CURSOR = "cursor";
    private static final String PARAM_INLINE = "inline";
    private static final String PARAM_ICON_EMOJI = "icon_emoji";
    private static final String PARAM_COVER_URL = "cover_url";
    private static final String PARAM_MODE = "mode";
    private static final String PARAM_FILENAME = "filename";
    private static final String PARAM_CONTENT_TYPE = "content_type";
    private static final String PARAM_NUMBER_OF_PARTS = "number_of_parts";
    private static final String PARAM_EXTERNAL_URL = "external_url";
    private static final String PARAM_LOCAL_PATH = "local_path";
    private static final String PARAM_FILE_UPLOAD_ID = "file_upload_id";
    private static final String PARAM_FILE_NAME = "file_name";
    private static final String PARAM_CAPTION = "caption";
    private static final String PARAM_BLOCK_TYPE = "block_type";
    private static final String PARAM_STATUS = "status";

    private final NotionVaultService service;

    public NotionVaultToolProvider(NotionVaultService service) {
        this.service = service;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PARAM_OPERATION, Map.of(
                TYPE, TYPE_STRING,
                "enum", List.of(
                        "list_directory",
                        "search_notes",
                        "read_note",
                        "create_note",
                        "update_note",
                        "delete_note",
                        "move_note",
                        "rename_note",
                        "create_database",
                        "read_database",
                        "update_database",
                        "create_data_source",
                        "read_data_source",
                        "update_data_source",
                        "query_database",
                        "create_database_entry",
                        "read_database_entry",
                        "update_database_entry",
                        "list_page_files",
                        "create_file_upload",
                        "upload_file_content",
                        "complete_file_upload",
                        "read_file_upload",
                        "list_file_uploads",
                        "attach_file_to_page")));
        properties.put(PARAM_QUERY, stringProperty("Full-text query against the local Notion index."));
        properties.put(PARAM_PATH, stringProperty("Pseudo-path to a Notion page or subtree."));
        properties.put(PARAM_LIMIT, integerProperty("Maximum number of results to return."));
        properties.put(PARAM_CONTENT, stringProperty("Markdown content for note or page creation."));
        properties.put(PARAM_CONTENT_JSON, stringProperty("JSON array of block children for page creation."));
        properties.put(PARAM_TARGET_PATH, stringProperty("Target pseudo-path for move_note."));
        properties.put(PARAM_NEW_NAME, stringProperty("New leaf page title for rename_note."));
        properties.put(PARAM_PARENT_PATH,
                stringProperty("Pseudo-path of the parent page used when creating a database."));
        properties.put(PARAM_TITLE, stringProperty("Human-readable title for a database or data source."));
        properties.put(PARAM_DATABASE_ID, stringProperty("Notion database ID."));
        properties.put(PARAM_DATA_SOURCE_ID, stringProperty("Notion data source ID."));
        properties.put(PARAM_PAGE_ID, stringProperty("Notion page ID."));
        properties.put(PARAM_DESCRIPTION, stringProperty("Optional description text."));
        properties.put(PARAM_PROPERTIES_JSON,
                stringProperty("JSON object with database schema or page property values."));
        properties.put(PARAM_FILTER_JSON, stringProperty("JSON object with a Notion data source filter."));
        properties.put(PARAM_SORTS_JSON, stringProperty("JSON array with Notion sort definitions."));
        properties.put(PARAM_CURSOR, stringProperty("Pagination cursor for data source or file upload listing."));
        properties.put(PARAM_INLINE, booleanProperty("Whether the new database should be inline."));
        properties.put(PARAM_ICON_EMOJI, stringProperty("Optional emoji icon."));
        properties.put(PARAM_COVER_URL, stringProperty("Optional external URL for a page or database cover."));
        properties.put(PARAM_MODE, stringProperty("File upload mode: single_part, multi_part, or external_url."));
        properties.put(PARAM_FILENAME, stringProperty("Upload filename."));
        properties.put(PARAM_CONTENT_TYPE, stringProperty("MIME type of the uploaded file."));
        properties.put(PARAM_NUMBER_OF_PARTS, integerProperty("Number of parts for multi-part uploads."));
        properties.put(PARAM_EXTERNAL_URL, stringProperty("Public external URL for importing or attaching a file."));
        properties.put(PARAM_LOCAL_PATH, stringProperty("Local filesystem path used by upload_file_content."));
        properties.put(PARAM_FILE_UPLOAD_ID, stringProperty("Notion file upload ID."));
        properties.put(PARAM_FILE_NAME, stringProperty("Display file name when attaching a file block."));
        properties.put(PARAM_CAPTION, stringProperty("Optional file block caption."));
        properties.put(PARAM_BLOCK_TYPE, stringProperty("File block type: file, image, pdf, audio, or video."));
        properties.put(PARAM_STATUS, stringProperty("Filter file uploads by status."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, TYPE_OBJECT);
        schema.put(PROPERTIES, properties);
        schema.put(REQUIRED, List.of(PARAM_OPERATION));
        schema.put("allOf", List.of(
                requiredWhen("search_notes", List.of(PARAM_QUERY)),
                requiredWhen("read_note", List.of(PARAM_PATH)),
                requiredWhen("create_note", List.of(PARAM_PATH, PARAM_CONTENT)),
                requiredWhen("update_note", List.of(PARAM_PATH, PARAM_CONTENT)),
                requiredWhen("delete_note", List.of(PARAM_PATH)),
                requiredWhen("move_note", List.of(PARAM_PATH, PARAM_TARGET_PATH)),
                requiredWhen("rename_note", List.of(PARAM_PATH, PARAM_NEW_NAME)),
                requiredWhen("create_database", List.of(PARAM_PARENT_PATH, PARAM_TITLE)),
                requiredWhen("read_database", List.of(PARAM_DATABASE_ID)),
                requiredWhen("update_database", List.of(PARAM_DATABASE_ID)),
                requiredWhen("create_data_source", List.of(PARAM_DATABASE_ID, PARAM_PROPERTIES_JSON)),
                requiredWhen("read_data_source", List.of(PARAM_DATA_SOURCE_ID)),
                requiredWhen("update_data_source", List.of(PARAM_DATA_SOURCE_ID)),
                requiredWhenAny("query_database", List.of(
                        List.of(PARAM_DATABASE_ID),
                        List.of(PARAM_DATA_SOURCE_ID))),
                requiredWhenAny("create_database_entry", List.of(
                        List.of(PARAM_DATABASE_ID, PARAM_PROPERTIES_JSON),
                        List.of(PARAM_DATA_SOURCE_ID, PARAM_PROPERTIES_JSON))),
                requiredWhen("read_database_entry", List.of(PARAM_PAGE_ID)),
                requiredWhen("update_database_entry", List.of(PARAM_PAGE_ID)),
                requiredWhen("list_page_files", List.of(PARAM_PAGE_ID)),
                requiredWhen("upload_file_content", List.of(PARAM_FILE_UPLOAD_ID, PARAM_LOCAL_PATH)),
                requiredWhen("complete_file_upload", List.of(PARAM_FILE_UPLOAD_ID)),
                requiredWhen("read_file_upload", List.of(PARAM_FILE_UPLOAD_ID)),
                requiredWhenAny("attach_file_to_page", List.of(
                        List.of(PARAM_PAGE_ID, PARAM_FILE_UPLOAD_ID),
                        List.of(PARAM_PAGE_ID, PARAM_EXTERNAL_URL)))));

        return ToolDefinition.builder()
                .name("notion_vault")
                .description("Use Notion pages, databases, and files through the official Notion HTTP API.")
                .inputSchema(schema)
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
        case "create_database" -> service.createDatabase(
                readString(parameters.get(PARAM_PARENT_PATH)),
                readString(parameters.get(PARAM_TITLE)),
                readString(parameters.get(PARAM_DESCRIPTION)),
                readString(parameters.get(PARAM_PROPERTIES_JSON)),
                readBoolean(parameters.get(PARAM_INLINE)),
                readString(parameters.get(PARAM_ICON_EMOJI)),
                readString(parameters.get(PARAM_COVER_URL)));
        case "read_database" -> service.readDatabase(readString(parameters.get(PARAM_DATABASE_ID)));
        case "update_database" -> service.updateDatabase(
                readString(parameters.get(PARAM_DATABASE_ID)),
                readString(parameters.get(PARAM_TITLE)),
                readString(parameters.get(PARAM_DESCRIPTION)),
                readString(parameters.get(PARAM_ICON_EMOJI)),
                readString(parameters.get(PARAM_COVER_URL)));
        case "create_data_source" -> service.createDataSource(
                readString(parameters.get(PARAM_DATABASE_ID)),
                readString(parameters.get(PARAM_TITLE)),
                readString(parameters.get(PARAM_PROPERTIES_JSON)),
                readString(parameters.get(PARAM_ICON_EMOJI)));
        case "read_data_source" -> service.readDataSource(readString(parameters.get(PARAM_DATA_SOURCE_ID)));
        case "update_data_source" -> service.updateDataSource(
                readString(parameters.get(PARAM_DATA_SOURCE_ID)),
                readString(parameters.get(PARAM_TITLE)),
                readString(parameters.get(PARAM_PROPERTIES_JSON)),
                readString(parameters.get(PARAM_ICON_EMOJI)));
        case "query_database" -> service.queryDatabase(
                readString(parameters.get(PARAM_DATABASE_ID)),
                readString(parameters.get(PARAM_DATA_SOURCE_ID)),
                readString(parameters.get(PARAM_FILTER_JSON)),
                readString(parameters.get(PARAM_SORTS_JSON)),
                readInteger(parameters.get(PARAM_LIMIT)),
                readString(parameters.get(PARAM_CURSOR)));
        case "create_database_entry" -> service.createDatabaseEntry(
                readString(parameters.get(PARAM_DATABASE_ID)),
                readString(parameters.get(PARAM_DATA_SOURCE_ID)),
                readString(parameters.get(PARAM_PROPERTIES_JSON)),
                readString(parameters.get(PARAM_CONTENT)),
                readString(parameters.get(PARAM_CONTENT_JSON)),
                readString(parameters.get(PARAM_ICON_EMOJI)),
                readString(parameters.get(PARAM_COVER_URL)));
        case "read_database_entry" -> service.readDatabaseEntry(readString(parameters.get(PARAM_PAGE_ID)));
        case "update_database_entry" -> service.updateDatabaseEntry(
                readString(parameters.get(PARAM_PAGE_ID)),
                readString(parameters.get(PARAM_PROPERTIES_JSON)),
                readString(parameters.get(PARAM_ICON_EMOJI)),
                readString(parameters.get(PARAM_COVER_URL)));
        case "list_page_files" -> service.listPageFiles(readString(parameters.get(PARAM_PAGE_ID)));
        case "create_file_upload" -> service.createFileUpload(
                readString(parameters.get(PARAM_MODE)),
                readString(parameters.get(PARAM_FILENAME)),
                readString(parameters.get(PARAM_CONTENT_TYPE)),
                readInteger(parameters.get(PARAM_NUMBER_OF_PARTS)),
                readString(parameters.get(PARAM_EXTERNAL_URL)));
        case "upload_file_content" -> service.uploadFileContent(
                readString(parameters.get(PARAM_FILE_UPLOAD_ID)),
                readString(parameters.get(PARAM_LOCAL_PATH)),
                readString(parameters.get(PARAM_CONTENT_TYPE)));
        case "complete_file_upload" -> service.completeFileUpload(readString(parameters.get(PARAM_FILE_UPLOAD_ID)));
        case "read_file_upload" -> service.readFileUpload(readString(parameters.get(PARAM_FILE_UPLOAD_ID)));
        case "list_file_uploads" -> service.listFileUploads(
                readString(parameters.get(PARAM_STATUS)),
                readInteger(parameters.get(PARAM_LIMIT)),
                readString(parameters.get(PARAM_CURSOR)));
        case "attach_file_to_page" -> service.attachFileToPage(
                readString(parameters.get(PARAM_PAGE_ID)),
                readString(parameters.get(PARAM_FILE_UPLOAD_ID)),
                readString(parameters.get(PARAM_EXTERNAL_URL)),
                readString(parameters.get(PARAM_FILE_NAME)),
                readString(parameters.get(PARAM_CAPTION)),
                readString(parameters.get(PARAM_BLOCK_TYPE)));
        default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "Unsupported notion_vault operation: " + operation);
        };
    }

    private Map<String, Object> stringProperty(String description) {
        return Map.of(TYPE, TYPE_STRING, "description", description);
    }

    private Map<String, Object> integerProperty(String description) {
        return Map.of(TYPE, TYPE_INTEGER, "description", description);
    }

    private Map<String, Object> booleanProperty(String description) {
        return Map.of(TYPE, TYPE_BOOLEAN, "description", description);
    }

    private Map<String, Object> requiredWhen(String operation, List<String> requiredFields) {
        return Map.of(
                "if", Map.of(
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of("const", operation))),
                "then", Map.of(
                        REQUIRED, requiredFields));
    }

    private Map<String, Object> requiredWhenAny(String operation, List<List<String>> alternatives) {
        return Map.of(
                "if", Map.of(
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of("const", operation))),
                "then", Map.of(
                        "anyOf", alternatives.stream()
                                .map(required -> Map.of(REQUIRED, required))
                                .toList()));
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

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}
