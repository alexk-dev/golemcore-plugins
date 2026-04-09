package me.golemcore.plugins.golemcore.airtable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiClient;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiException;
import me.golemcore.plugins.golemcore.airtable.support.AirtableTransportException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AirtableRecordsService {

    private final AirtableApiClient apiClient;
    private final AirtablePluginConfigService configService;
    private final ObjectMapper objectMapper;

    public AirtableRecordsService(AirtableApiClient apiClient, AirtablePluginConfigService configService) {
        this.apiClient = apiClient;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
    }

    public AirtablePluginConfig getConfig() {
        return configService.getConfig();
    }

    public ToolResult listRecords(
            String table,
            String view,
            String filterByFormula,
            Integer maxRecords,
            List<String> fieldNames,
            String sortField,
            String sortDirection) {
        try {
            AirtablePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedView = hasText(view) ? view.trim() : config.getDefaultView();
            int resolvedMaxRecords = normalizeMaxRecords(maxRecords, config.getDefaultMaxRecords());
            List<String> normalizedFieldNames = normalizeFieldNames(fieldNames);
            String normalizedSortField = normalizeOptional(sortField);
            String normalizedSortDirection = normalizeSortDirection(sortDirection);
            AirtableApiClient.AirtableListResponse response = apiClient.listRecords(
                    resolvedTable,
                    resolvedView,
                    normalizeOptional(filterByFormula),
                    resolvedMaxRecords,
                    normalizedFieldNames,
                    normalizedSortField,
                    normalizedSortDirection);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("count", response.records().size());
            if (hasText(response.offset())) {
                data.put("next_offset", response.offset());
            }
            data.put("records", response.records().stream().map(this::toRecordMap).toList());
            return ToolResult.success(buildListOutput(resolvedTable, resolvedView, response), data);
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult getRecord(String table, String recordId) {
        try {
            AirtablePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedRecordId = requireRecordId(recordId);
            AirtableApiClient.AirtableRecord record = apiClient.getRecord(resolvedTable, resolvedRecordId);
            Map<String, Object> data = toRecordMap(record);
            data.put("table", resolvedTable);
            return ToolResult.success(
                    "Airtable record " + record.id() + " in " + resolvedTable + ":\n"
                            + serializeJson(record.fields()),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult createRecord(String table, Map<String, Object> fields, Optional<Boolean> typecastOverride) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Airtable write is disabled in plugin settings");
        }
        try {
            AirtablePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            Map<String, Object> normalizedFields = requireFields(fields);
            boolean typecast = resolveTypecast(typecastOverride, config);
            AirtableApiClient.AirtableRecord record = apiClient.createRecord(resolvedTable, normalizedFields, typecast);
            Map<String, Object> data = toRecordMap(record);
            data.put("table", resolvedTable);
            return ToolResult.success(
                    "Created Airtable record " + record.id() + " in " + resolvedTable + ":\n"
                            + serializeJson(record.fields()),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateRecord(
            String table,
            String recordId,
            Map<String, Object> fields,
            Optional<Boolean> typecastOverride) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Airtable write is disabled in plugin settings");
        }
        try {
            AirtablePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedRecordId = requireRecordId(recordId);
            Map<String, Object> normalizedFields = requireFields(fields);
            boolean typecast = resolveTypecast(typecastOverride, config);
            AirtableApiClient.AirtableRecord record = apiClient.updateRecord(
                    resolvedTable,
                    resolvedRecordId,
                    normalizedFields,
                    typecast);
            Map<String, Object> data = toRecordMap(record);
            data.put("table", resolvedTable);
            return ToolResult.success(
                    "Updated Airtable record " + record.id() + " in " + resolvedTable + ":\n"
                            + serializeJson(record.fields()),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult deleteRecord(String table, String recordId) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Airtable delete is disabled in plugin settings");
        }
        try {
            AirtablePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedRecordId = requireRecordId(recordId);
            AirtableApiClient.AirtableDeleteResponse response = apiClient.deleteRecord(resolvedTable, resolvedRecordId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("record_id", response.id());
            data.put("deleted", response.deleted());
            return ToolResult.success(
                    "Deleted Airtable record " + response.id() + " from " + resolvedTable + '.',
                    data);
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private AirtablePluginConfig requireConfigured() {
        AirtablePluginConfig config = configService.getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException("Airtable plugin is disabled in plugin settings");
        }
        if (!hasText(config.getApiToken())) {
            throw new IllegalStateException("Airtable API token is not configured.");
        }
        if (!hasText(config.getBaseId())) {
            throw new IllegalStateException("Airtable base ID is not configured.");
        }
        return config;
    }

    private String resolveTable(String table, AirtablePluginConfig config) {
        String resolvedTable = hasText(table) ? table.trim() : config.getDefaultTable();
        if (!hasText(resolvedTable)) {
            throw new IllegalArgumentException(
                    "Airtable table is required. Configure a default table or pass the table parameter.");
        }
        return resolvedTable;
    }

    private String requireRecordId(String recordId) {
        if (!hasText(recordId)) {
            throw new IllegalArgumentException("record_id is required");
        }
        return recordId.trim();
    }

    private Map<String, Object> requireFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must contain at least one Airtable field");
        }
        return fields;
    }

    private int normalizeMaxRecords(Integer requestedMaxRecords, int defaultMaxRecords) {
        int resolved = requestedMaxRecords != null ? requestedMaxRecords : defaultMaxRecords;
        if (resolved <= 0) {
            return AirtablePluginConfig.DEFAULT_MAX_RECORDS;
        }
        return Math.min(resolved, AirtablePluginConfig.MAX_RECORDS_LIMIT);
    }

    private List<String> normalizeFieldNames(List<String> fieldNames) {
        List<String> normalized = new ArrayList<>();
        if (fieldNames == null) {
            return normalized;
        }
        for (String fieldName : fieldNames) {
            if (hasText(fieldName) && !normalized.contains(fieldName.trim())) {
                normalized.add(fieldName.trim());
            }
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeSortDirection(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(normalized) || "desc".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("sort_direction must be asc or desc");
    }

    private boolean resolveTypecast(Optional<Boolean> typecastOverride, AirtablePluginConfig config) {
        return typecastOverride.orElse(Boolean.TRUE.equals(config.getTypecast()));
    }

    private String buildListOutput(
            String table,
            String view,
            AirtableApiClient.AirtableListResponse response) {
        if (response.records().isEmpty()) {
            return "No Airtable records found in " + table + '.';
        }
        StringBuilder output = new StringBuilder();
        output.append("Airtable records from ")
                .append(table)
                .append(" (")
                .append(response.records().size())
                .append(" record(s)");
        if (hasText(view)) {
            output.append(", view=").append(view);
        }
        output.append("):\n\n");
        for (int index = 0; index < response.records().size(); index++) {
            AirtableApiClient.AirtableRecord record = response.records().get(index);
            output.append(index + 1)
                    .append(". ")
                    .append(record.id())
                    .append('\n')
                    .append(serializeJson(record.fields()))
                    .append("\n\n");
        }
        if (hasText(response.offset())) {
            output.append("More records are available. Next offset: ")
                    .append(response.offset());
        }
        return output.toString().trim();
    }

    private Map<String, Object> toRecordMap(AirtableApiClient.AirtableRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.id());
        data.put("created_time", record.createdTime());
        data.put("fields", record.fields());
        return data;
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private ToolResult executionFailure(String message) {
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
