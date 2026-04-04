package me.golemcore.plugins.golemcore.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class SupabaseRowsToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_TABLE = "table";
    private static final String PARAM_SCHEMA = "schema";
    private static final String PARAM_SELECT = "select";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_OFFSET = "offset";
    private static final String PARAM_ORDER_BY = "order_by";
    private static final String PARAM_ASCENDING = "ascending";
    private static final String PARAM_FILTERS = "filters";
    private static final String PARAM_VALUES = "values";

    private final SupabaseRowsService service;
    private final ObjectMapper objectMapper;

    public SupabaseRowsToolProvider(SupabaseRowsService service) {
        this.service = service;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean isEnabled() {
        SupabasePluginConfig config = service.getConfig();
        return Boolean.TRUE.equals(config.getEnabled())
                && hasText(config.getProjectUrl())
                && hasText(config.getApiKey());
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("supabase_rows")
                .description(
                        "Query and mutate Supabase PostgREST table rows using filters, select expressions, and JSON values.")
                .inputSchema(Map.ofEntries(
                        Map.entry(TYPE, TYPE_OBJECT),
                        Map.entry(PROPERTIES, Map.ofEntries(
                                Map.entry(PARAM_OPERATION, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("enum", List.of(
                                                "select_rows",
                                                "insert_row",
                                                "update_rows",
                                                "delete_rows")))),
                                Map.entry(PARAM_TABLE, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Optional table name. Uses the configured default table when omitted."))),
                                Map.entry(PARAM_SCHEMA, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Optional Postgres schema. Defaults to the configured schema."))),
                                Map.entry(PARAM_SELECT, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "PostgREST select expression, for example id,name,status."))),
                                Map.entry(PARAM_LIMIT, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_INTEGER),
                                        Map.entry("description",
                                                "Maximum number of rows to return for select_rows."))),
                                Map.entry(PARAM_OFFSET, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_INTEGER),
                                        Map.entry("description", "Optional row offset for select_rows."))),
                                Map.entry(PARAM_ORDER_BY, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Optional column used for ordering select_rows."))),
                                Map.entry(PARAM_ASCENDING, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_BOOLEAN),
                                        Map.entry("description",
                                                "Optional sort direction flag for select_rows."))),
                                Map.entry(PARAM_FILTERS, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_OBJECT),
                                        Map.entry("description",
                                                "Map of column to PostgREST filter expression, for example {status: \"eq.active\"}. A JSON string is also accepted."))),
                                Map.entry(PARAM_VALUES, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_OBJECT),
                                        Map.entry("description",
                                                "Map of column values for insert_row or update_rows. A JSON string is also accepted."))))),
                        Map.entry(REQUIRED, List.of(PARAM_OPERATION)),
                        Map.entry("allOf", List.of(
                                requiredWhen("insert_row", List.of(PARAM_VALUES)),
                                requiredWhen("update_rows", List.of(PARAM_FILTERS, PARAM_VALUES)),
                                requiredWhen("delete_rows", List.of(PARAM_FILTERS))))))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeOperation(parameters));
    }

    private ToolResult executeOperation(Map<String, Object> parameters) {
        try {
            String operation = readString(parameters.get(PARAM_OPERATION));
            if (!hasText(operation)) {
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "operation is required");
            }
            return switch (operation) {
            case "select_rows" -> service.selectRows(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_SCHEMA)),
                    readString(parameters.get(PARAM_SELECT)),
                    readInteger(parameters.get(PARAM_LIMIT)),
                    readInteger(parameters.get(PARAM_OFFSET)),
                    readString(parameters.get(PARAM_ORDER_BY)),
                    readOptionalBoolean(parameters, PARAM_ASCENDING),
                    readFilterMap(parameters.get(PARAM_FILTERS)));
            case "insert_row" -> service.insertRow(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_SCHEMA)),
                    readValueMap(parameters.get(PARAM_VALUES)));
            case "update_rows" -> service.updateRows(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_SCHEMA)),
                    readFilterMap(parameters.get(PARAM_FILTERS)),
                    readValueMap(parameters.get(PARAM_VALUES)));
            case "delete_rows" -> service.deleteRows(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_SCHEMA)),
                    readFilterMap(parameters.get(PARAM_FILTERS)));
            default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Unsupported supabase_rows operation: " + operation);
            };
        } catch (IllegalArgumentException ex) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, ex.getMessage());
        }
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
        return value instanceof String text ? text.trim() : null;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("limit and offset must be integers");
            }
        }
        return null;
    }

    private Optional<Boolean> readOptionalBoolean(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) {
            return Optional.empty();
        }
        Object value = parameters.get(key);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        throw new IllegalArgumentException(key + " must be a boolean");
    }

    private Map<String, String> readFilterMap(Object value) {
        Map<String, Object> raw = readObjectMap(value, "filters");
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Object filterValue = entry.getValue();
            if (filterValue != null) {
                normalized.put(entry.getKey(), filterValue.toString());
            }
        }
        return normalized;
    }

    private Map<String, Object> readValueMap(Object value) {
        return readObjectMap(value, "values");
    }

    private Map<String, Object> readObjectMap(Object value, String fieldName) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalized.put(entry.getKey().toString(), entry.getValue());
            }
            return normalized;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<>() {
                });
            } catch (Exception ex) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object or map");
            }
        }
        throw new IllegalArgumentException(fieldName + " must be a JSON object or map");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
