package me.golemcore.plugins.golemcore.brain;

import org.springframework.beans.factory.annotation.Autowired;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class BrainToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_SPACE_SLUG = "space_slug";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_CONTEXT = "context";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_PARENT_PATH = "parent_path";
    private static final String PARAM_TITLE = "title";
    private static final String PARAM_SLUG = "slug";
    private static final String PARAM_CONTENT = "content";
    private static final String PARAM_KIND = "kind";
    private static final String PARAM_REVISION = "revision";
    private static final String PARAM_TARGET_TITLE = "target_title";
    private static final String PARAM_TARGET_PARENT_PATH = "target_parent_path";
    private static final String PARAM_TARGET_SLUG = "target_slug";
    private static final String PARAM_BEFORE_SLUG = "before_slug";
    private static final String PARAM_DYNAMIC_API_SLUG = "dynamic_api_slug";
    private static final String PARAM_USE_DYNAMIC_ENDPOINT = "use_dynamic_endpoint";

    private final BrainPluginConfigService configService;
    private final BrainService service;

    @Autowired
    public BrainToolProvider(BrainPluginConfigService configService) {
        this(configService, new OkHttpClient());
    }

    BrainToolProvider(BrainPluginConfigService configService, OkHttpClient httpClient) {
        this.configService = configService;
        this.service = new BrainService(configService, httpClient);
    }

    @Override
    public boolean isEnabled() {
        return service.isAvailable();
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PARAM_OPERATION, Map.of(
                TYPE, TYPE_STRING,
                "enum", List.of(
                        "list_spaces",
                        "list_tree",
                        "search_pages",
                        "intellisearch",
                        "read_page",
                        "create_page",
                        "update_page",
                        "delete_page",
                        "ensure_page",
                        "move_page",
                        "copy_page",
                        "list_assets")));
        properties.put(PARAM_SPACE_SLUG, stringProperty("Brain space slug. Defaults to plugin settings."));
        properties.put(PARAM_QUERY, stringProperty("Search query."));
        properties.put(PARAM_CONTEXT, stringProperty("Natural language context for intellisearch."));
        properties.put(PARAM_LIMIT, integerProperty("Maximum number of results or pages."));
        properties.put(PARAM_PATH, stringProperty("Brain page path."));
        properties.put(PARAM_PARENT_PATH, stringProperty(
                "Parent section path for create_page. If the exact path is not found, the Brain plugin also tries a slugified title form."));
        properties.put(PARAM_TITLE, stringProperty("Page title."));
        properties.put(PARAM_SLUG, stringProperty("Optional page slug."));
        properties.put(PARAM_CONTENT, stringProperty("Markdown page content."));
        properties.put(PARAM_KIND, stringProperty("Brain node kind: PAGE or SECTION."));
        properties.put(PARAM_REVISION, stringProperty("Optional optimistic-lock revision for update_page."));
        properties.put(PARAM_TARGET_TITLE, stringProperty("Target title for ensure_page."));
        properties.put(PARAM_TARGET_PARENT_PATH, stringProperty("Target parent path for move_page or copy_page."));
        properties.put(PARAM_TARGET_SLUG, stringProperty("Target slug for move_page or copy_page."));
        properties.put(PARAM_BEFORE_SLUG, stringProperty("Sibling slug to insert before when moving or copying."));
        properties.put(PARAM_DYNAMIC_API_SLUG, stringProperty("Optional Brain dynamic API slug for intellisearch."));
        properties.put(PARAM_USE_DYNAMIC_ENDPOINT,
                booleanProperty("Set false to force fallback search even when a dynamic endpoint is configured."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, TYPE_OBJECT);
        schema.put(PROPERTIES, properties);
        schema.put(REQUIRED, List.of(PARAM_OPERATION));
        schema.put("allOf", List.of(
                requiredWhen("search_pages", List.of(PARAM_QUERY)),
                requiredWhenAny("intellisearch", List.of(List.of(PARAM_CONTEXT), List.of(PARAM_QUERY))),
                requiredWhen("read_page", List.of(PARAM_PATH)),
                requiredWhen("create_page", List.of(PARAM_TITLE)),
                requiredWhen("update_page", List.of(PARAM_PATH, PARAM_CONTENT)),
                requiredWhen("delete_page", List.of(PARAM_PATH)),
                requiredWhen("ensure_page", List.of(PARAM_PATH)),
                requiredWhen("move_page", List.of(PARAM_PATH, PARAM_TARGET_PARENT_PATH)),
                requiredWhen("copy_page", List.of(PARAM_PATH, PARAM_TARGET_PARENT_PATH)),
                requiredWhen("list_assets", List.of(PARAM_PATH))));

        return ToolDefinition.builder()
                .name("golemcore_brain")
                .description("Use GolemCore Brain wiki pages, spaces, search, and optional dynamic intellisearch.")
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
        if (!isEnabled()) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "GolemCore Brain tool is disabled or base URL is missing");
        }
        try {
            return switch (operation) {
            case "list_spaces" -> service.listSpaces();
            case "list_tree" -> service.listTree(readString(parameters.get(PARAM_SPACE_SLUG)));
            case "search_pages" -> service.searchPages(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_QUERY)),
                    readInteger(parameters.get(PARAM_LIMIT)));
            case "intellisearch" -> service.intellisearch(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_CONTEXT)),
                    readString(parameters.get(PARAM_QUERY)),
                    readInteger(parameters.get(PARAM_LIMIT)),
                    readString(parameters.get(PARAM_DYNAMIC_API_SLUG)),
                    readBoolean(parameters.get(PARAM_USE_DYNAMIC_ENDPOINT)));
            case "read_page" -> service.readPage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)));
            case "create_page" -> service.createPage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PARENT_PATH)),
                    readString(parameters.get(PARAM_TITLE)),
                    readString(parameters.get(PARAM_SLUG)),
                    readString(parameters.get(PARAM_CONTENT)),
                    readString(parameters.get(PARAM_KIND)));
            case "update_page" -> service.updatePage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)),
                    readString(parameters.get(PARAM_TITLE)),
                    readString(parameters.get(PARAM_SLUG)),
                    readString(parameters.get(PARAM_CONTENT)),
                    readString(parameters.get(PARAM_REVISION)));
            case "delete_page" -> service.deletePage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)));
            case "ensure_page" -> service.ensurePage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)),
                    readString(parameters.get(PARAM_TARGET_TITLE)));
            case "move_page" -> service.movePage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)),
                    readString(parameters.get(PARAM_TARGET_PARENT_PATH)),
                    readString(parameters.get(PARAM_TARGET_SLUG)),
                    readString(parameters.get(PARAM_BEFORE_SLUG)));
            case "copy_page" -> service.copyPage(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)),
                    readString(parameters.get(PARAM_TARGET_PARENT_PATH)),
                    readString(parameters.get(PARAM_TARGET_SLUG)),
                    readString(parameters.get(PARAM_BEFORE_SLUG)));
            case "list_assets" -> service.listAssets(
                    readString(parameters.get(PARAM_SPACE_SLUG)),
                    readString(parameters.get(PARAM_PATH)));
            default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Unknown Brain operation: " + operation);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, exception.getMessage());
        }
    }

    private static Map<String, Object> requiredWhen(String operation, List<String> requiredFields) {
        return Map.of(
                "if", Map.of(PROPERTIES, Map.of(PARAM_OPERATION, Map.of("const", operation))),
                "then", Map.of(REQUIRED, requiredFields));
    }

    private static Map<String, Object> requiredWhenAny(String operation, List<List<String>> alternatives) {
        return Map.of(
                "if", Map.of(PROPERTIES, Map.of(PARAM_OPERATION, Map.of("const", operation))),
                "then", Map.of("anyOf", alternatives.stream()
                        .map(required -> Map.of(REQUIRED, required))
                        .toList()));
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of(TYPE, TYPE_STRING, "description", description);
    }

    private static Map<String, Object> integerProperty(String description) {
        return Map.of(TYPE, TYPE_INTEGER, "description", description);
    }

    private static Map<String, Object> booleanProperty(String description) {
        return Map.of(TYPE, TYPE_BOOLEAN, "description", description);
    }

    private String readString(Object value) {
        return value instanceof String text ? text : null;
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
        return !(value instanceof Boolean bool) || bool;
    }
}
