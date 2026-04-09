package me.golemcore.plugins.golemcore.airtable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class AirtableRecordsToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_ARRAY = "array";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ITEMS = "items";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_TABLE = "table";
    private static final String PARAM_VIEW = "view";
    private static final String PARAM_FILTER_BY_FORMULA = "filter_by_formula";
    private static final String PARAM_MAX_RECORDS = "max_records";
    private static final String PARAM_FIELD_NAMES = "field_names";
    private static final String PARAM_SORT_FIELD = "sort_field";
    private static final String PARAM_SORT_DIRECTION = "sort_direction";
    private static final String PARAM_RECORD_ID = "record_id";
    private static final String PARAM_FIELDS = "fields";
    private static final String PARAM_TYPECAST = "typecast";

    private final AirtableRecordsService service;
    private final ObjectMapper objectMapper;

    public AirtableRecordsToolProvider(AirtableRecordsService service) {
        this.service = service;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean isEnabled() {
        AirtablePluginConfig config = service.getConfig();
        return Boolean.TRUE.equals(config.getEnabled())
                && hasText(config.getApiToken())
                && hasText(config.getBaseId());
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("airtable_records")
                .description("List, read, create, update, and delete Airtable records through the Airtable REST API.")
                .inputSchema(Map.ofEntries(
                        Map.entry(TYPE, TYPE_OBJECT),
                        Map.entry(PROPERTIES, Map.ofEntries(
                                Map.entry(PARAM_OPERATION, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("enum", List.of(
                                                "list_records",
                                                "get_record",
                                                "create_record",
                                                "update_record",
                                                "delete_record")))),
                                Map.entry(PARAM_TABLE, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description", "Optional Airtable table name or table ID."))),
                                Map.entry(PARAM_VIEW, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description", "Optional Airtable view used for list_records."))),
                                Map.entry(PARAM_FILTER_BY_FORMULA, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Optional Airtable filterByFormula value for list_records."))),
                                Map.entry(PARAM_MAX_RECORDS, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_INTEGER),
                                        Map.entry("description",
                                                "Maximum number of records to return for list_records (1-100)."))),
                                Map.entry(PARAM_FIELD_NAMES, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_ARRAY),
                                        Map.entry(ITEMS, Map.of(TYPE, TYPE_STRING)),
                                        Map.entry("description",
                                                "Optional list of Airtable field names to include in list_records."))),
                                Map.entry(PARAM_SORT_FIELD, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Optional Airtable field used for sorting list_records."))),
                                Map.entry(PARAM_SORT_DIRECTION, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("enum", List.of("asc", "desc")),
                                        Map.entry("description", "Optional sort direction for list_records."))),
                                Map.entry(PARAM_RECORD_ID, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_STRING),
                                        Map.entry("description",
                                                "Airtable record ID for get_record, update_record, or delete_record."))),
                                Map.entry(PARAM_FIELDS, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_OBJECT),
                                        Map.entry("description",
                                                "Field map for create_record or update_record. A JSON string is also accepted for convenience."))),
                                Map.entry(PARAM_TYPECAST, Map.ofEntries(
                                        Map.entry(TYPE, TYPE_BOOLEAN),
                                        Map.entry("description",
                                                "Optional override for Airtable typecast on create/update."))))),
                        Map.entry(REQUIRED, List.of(PARAM_OPERATION)),
                        Map.entry("allOf", List.of(
                                requiredWhen("get_record", List.of(PARAM_RECORD_ID)),
                                requiredWhen("create_record", List.of(PARAM_FIELDS)),
                                requiredWhen("update_record", List.of(PARAM_RECORD_ID, PARAM_FIELDS)),
                                requiredWhen("delete_record", List.of(PARAM_RECORD_ID))))))
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
            case "list_records" -> service.listRecords(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_VIEW)),
                    readString(parameters.get(PARAM_FILTER_BY_FORMULA)),
                    readInteger(parameters.get(PARAM_MAX_RECORDS)),
                    readStringList(parameters.get(PARAM_FIELD_NAMES)),
                    readString(parameters.get(PARAM_SORT_FIELD)),
                    readString(parameters.get(PARAM_SORT_DIRECTION)));
            case "get_record" -> service.getRecord(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_RECORD_ID)));
            case "create_record" -> service.createRecord(
                    readString(parameters.get(PARAM_TABLE)),
                    readFields(parameters.get(PARAM_FIELDS)),
                    readOptionalBoolean(parameters, PARAM_TYPECAST));
            case "update_record" -> service.updateRecord(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_RECORD_ID)),
                    readFields(parameters.get(PARAM_FIELDS)),
                    readOptionalBoolean(parameters, PARAM_TYPECAST));
            case "delete_record" -> service.deleteRecord(
                    readString(parameters.get(PARAM_TABLE)),
                    readString(parameters.get(PARAM_RECORD_ID)));
            default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Unsupported airtable_records operation: " + operation);
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
                throw new IllegalArgumentException("max_records must be an integer");
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

    private List<String> readStringList(Object value) {
        Set<String> normalized = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = readString(item);
                if (hasText(text)) {
                    normalized.add(text);
                }
            }
        } else if (value instanceof String text) {
            String[] parts = text.split("[,\\n]");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, Object> readFields(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("fields must be a JSON object or map");
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
                throw new IllegalArgumentException("fields must be a JSON object or map");
            }
        }
        throw new IllegalArgumentException("fields must be a JSON object or map");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
