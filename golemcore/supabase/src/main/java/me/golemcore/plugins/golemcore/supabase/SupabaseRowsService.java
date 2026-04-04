package me.golemcore.plugins.golemcore.supabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiClient;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiException;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseTransportException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SupabaseRowsService {

    private final SupabaseApiClient apiClient;
    private final SupabasePluginConfigService configService;
    private final ObjectMapper objectMapper;

    public SupabaseRowsService(SupabaseApiClient apiClient, SupabasePluginConfigService configService) {
        this.apiClient = apiClient;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
    }

    public SupabasePluginConfig getConfig() {
        return configService.getConfig();
    }

    public ToolResult selectRows(
            String table,
            String schema,
            String select,
            Integer limit,
            Integer offset,
            String orderBy,
            Optional<Boolean> ascending,
            Map<String, String> filters) {
        try {
            SupabasePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedSchema = resolveSchema(schema, config);
            String resolvedSelect = hasText(select) ? select.trim() : config.getDefaultSelect();
            int resolvedLimit = normalizeLimit(limit, config.getDefaultLimit());
            List<Map<String, Object>> rows = apiClient.selectRows(
                    resolvedTable,
                    resolvedSchema,
                    resolvedSelect,
                    resolvedLimit,
                    offset,
                    normalizeOptional(orderBy),
                    ascending,
                    filters);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("schema", resolvedSchema);
            data.put("count", rows.size());
            data.put("rows", rows);
            return ToolResult.success(buildRowsOutput("Supabase rows", resolvedSchema, resolvedTable, rows), data);
        } catch (IllegalArgumentException | IllegalStateException | SupabaseApiException
                | SupabaseTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult insertRow(String table, String schema, Map<String, Object> values) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Supabase write is disabled in plugin settings");
        }
        try {
            SupabasePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedSchema = resolveSchema(schema, config);
            Map<String, Object> normalizedValues = requireValues(values);
            List<Map<String, Object>> rows = apiClient.insertRow(resolvedTable, resolvedSchema, normalizedValues);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("schema", resolvedSchema);
            data.put("count", rows.size());
            data.put("rows", rows);
            return ToolResult.success(
                    buildRowsOutput("Inserted Supabase rows into", resolvedSchema, resolvedTable, rows),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | SupabaseApiException
                | SupabaseTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult updateRows(
            String table,
            String schema,
            Map<String, String> filters,
            Map<String, Object> values) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowWrite())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Supabase write is disabled in plugin settings");
        }
        try {
            SupabasePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedSchema = resolveSchema(schema, config);
            Map<String, String> normalizedFilters = requireFilters(filters);
            Map<String, Object> normalizedValues = requireValues(values);
            List<Map<String, Object>> rows = apiClient.updateRows(
                    resolvedTable,
                    resolvedSchema,
                    normalizedFilters,
                    normalizedValues);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("schema", resolvedSchema);
            data.put("count", rows.size());
            data.put("rows", rows);
            return ToolResult.success(buildRowsOutput("Updated Supabase rows in", resolvedSchema, resolvedTable, rows),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | SupabaseApiException
                | SupabaseTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    public ToolResult deleteRows(String table, String schema, Map<String, String> filters) {
        if (!Boolean.TRUE.equals(configService.getConfig().getAllowDelete())) {
            return ToolResult.failure(ToolFailureKind.POLICY_DENIED,
                    "Supabase delete is disabled in plugin settings");
        }
        try {
            SupabasePluginConfig config = requireConfigured();
            String resolvedTable = resolveTable(table, config);
            String resolvedSchema = resolveSchema(schema, config);
            Map<String, String> normalizedFilters = requireFilters(filters);
            List<Map<String, Object>> rows = apiClient.deleteRows(resolvedTable, resolvedSchema, normalizedFilters);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("table", resolvedTable);
            data.put("schema", resolvedSchema);
            data.put("count", rows.size());
            data.put("rows", rows);
            return ToolResult.success(
                    buildRowsOutput("Deleted Supabase rows from", resolvedSchema, resolvedTable, rows),
                    data);
        } catch (IllegalArgumentException | IllegalStateException | SupabaseApiException
                | SupabaseTransportException ex) {
            return executionFailure(ex.getMessage());
        }
    }

    private SupabasePluginConfig requireConfigured() {
        SupabasePluginConfig config = configService.getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException("Supabase plugin is disabled in plugin settings");
        }
        if (!hasText(config.getProjectUrl())) {
            throw new IllegalStateException("Supabase project URL is not configured.");
        }
        if (!hasText(config.getApiKey())) {
            throw new IllegalStateException("Supabase API key is not configured.");
        }
        return config;
    }

    private String resolveTable(String table, SupabasePluginConfig config) {
        String resolvedTable = hasText(table) ? table.trim() : config.getDefaultTable();
        if (!hasText(resolvedTable)) {
            throw new IllegalArgumentException(
                    "Supabase table is required. Configure a default table or pass the table parameter.");
        }
        return resolvedTable;
    }

    private String resolveSchema(String schema, SupabasePluginConfig config) {
        return hasText(schema) ? schema.trim() : config.getDefaultSchema();
    }

    private int normalizeLimit(Integer requestedLimit, int defaultLimit) {
        int resolved = requestedLimit != null ? requestedLimit : defaultLimit;
        if (resolved <= 0) {
            return SupabasePluginConfig.DEFAULT_LIMIT;
        }
        return Math.min(resolved, SupabasePluginConfig.MAX_LIMIT);
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private Map<String, String> requireFilters(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException(
                    "filters must contain at least one PostgREST filter expression for update_rows or delete_rows");
        }
        return filters;
    }

    private Map<String, Object> requireValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must contain at least one column value");
        }
        return values;
    }

    private String buildRowsOutput(String action, String schema, String table, List<Map<String, Object>> rows) {
        String qualifiedTable = schema + "." + table;
        if (rows.isEmpty()) {
            return action + " " + qualifiedTable + ": 0 row(s) matched.";
        }
        StringBuilder output = new StringBuilder();
        output.append(action)
                .append(' ')
                .append(qualifiedTable)
                .append(" (")
                .append(rows.size())
                .append(" row(s)):\n\n");
        for (int index = 0; index < rows.size(); index++) {
            output.append(index + 1)
                    .append(". ")
                    .append(serializeJson(rows.get(index)))
                    .append("\n\n");
        }
        return output.toString().trim();
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
