package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupabaseRowsToolProviderTest {

    private SupabaseRowsService service;
    private SupabaseRowsToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(SupabaseRowsService.class);
        provider = new SupabaseRowsToolProvider(service);
        SupabasePluginConfig config = SupabasePluginConfig.builder()
                .enabled(true)
                .projectUrl("https://project.supabase.co")
                .apiKey("token")
                .build();
        when(service.getConfig()).thenReturn(config);
    }

    @Test
    void shouldParseJsonMapsForSelectRows() {
        when(service.selectRows("tasks", "public", "id,name", 5, null, "id", Optional.of(false),
                Map.of("status", "eq.active")))
                .thenReturn(ToolResult.success("selected"));

        ToolResult result = provider.execute(Map.of(
                "operation", "select_rows",
                "table", "tasks",
                "schema", "public",
                "select", "id,name",
                "limit", 5,
                "order_by", "id",
                "ascending", false,
                "filters", "{\"status\":\"eq.active\"}")).join();

        assertTrue(result.isSuccess());
        verify(service).selectRows("tasks", "public", "id,name", 5, null, "id", Optional.of(false),
                Map.of("status", "eq.active"));
    }

    @Test
    void shouldParseJsonValuesWhenInserting() {
        when(service.insertRow("tasks", "public", Map.of("name", "Alice")))
                .thenReturn(ToolResult.success("inserted"));

        ToolResult result = provider.execute(Map.of(
                "operation", "insert_row",
                "table", "tasks",
                "schema", "public",
                "values", "{\"name\":\"Alice\"}")).join();

        assertTrue(result.isSuccess());
        verify(service).insertRow("tasks", "public", Map.of("name", "Alice"));
    }

    @Test
    void shouldRejectInvalidFilterPayload() {
        ToolResult result = provider.execute(Map.of(
                "operation", "delete_rows",
                "filters", "not-json")).join();

        assertFalse(result.isSuccess());
        assertEquals("filters must be a JSON object or map", result.getError());
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals("Unsupported supabase_rows operation: unknown", result.getError());
    }
}
