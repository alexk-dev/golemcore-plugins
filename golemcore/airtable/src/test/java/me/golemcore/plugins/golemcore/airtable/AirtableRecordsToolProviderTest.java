package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.extension.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AirtableRecordsToolProviderTest {

    private AirtableRecordsService service;
    private AirtableRecordsToolProvider provider;

    @BeforeEach
    void setUp() {
        service = mock(AirtableRecordsService.class);
        provider = new AirtableRecordsToolProvider(service);
        AirtablePluginConfig config = AirtablePluginConfig.builder()
                .enabled(true)
                .apiToken("token")
                .baseId("appBase")
                .build();
        when(service.getConfig()).thenReturn(config);
    }

    @Test
    void shouldParseJsonFieldsWhenCreatingRecord() {
        when(service.createRecord("Tasks", Map.of("Name", "Alice"), Optional.of(true)))
                .thenReturn(ToolResult.success("created"));

        ToolResult result = provider.execute(Map.of(
                "operation", "create_record",
                "table", "Tasks",
                "fields", "{\"Name\":\"Alice\"}",
                "typecast", true)).join();

        assertTrue(result.isSuccess());
        verify(service).createRecord("Tasks", Map.of("Name", "Alice"), Optional.of(true));
    }

    @Test
    void shouldPassListParametersToService() {
        when(service.listRecords("Tasks", "Grid", "Status='Open'", 5, List.of("Name", "Status"),
                "Created", "desc"))
                .thenReturn(ToolResult.success("listed"));

        ToolResult result = provider.execute(Map.of(
                "operation", "list_records",
                "table", "Tasks",
                "view", "Grid",
                "filter_by_formula", "Status='Open'",
                "max_records", 5,
                "field_names", "Name,Status",
                "sort_field", "Created",
                "sort_direction", "desc")).join();

        assertTrue(result.isSuccess());
        verify(service).listRecords("Tasks", "Grid", "Status='Open'", 5, List.of("Name", "Status"),
                "Created", "desc");
    }

    @Test
    void shouldRejectInvalidFieldsPayload() {
        ToolResult result = provider.execute(Map.of(
                "operation", "create_record",
                "fields", "not-json")).join();

        assertFalse(result.isSuccess());
        assertEquals("fields must be a JSON object or map", result.getError());
    }

    @Test
    void shouldRejectUnsupportedOperation() {
        ToolResult result = provider.execute(Map.of("operation", "unknown")).join();

        assertFalse(result.isSuccess());
        assertEquals("Unsupported airtable_records operation: unknown", result.getError());
    }
}
