package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiClient;
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

class AirtableRecordsServiceTest {

    private AirtableApiClient apiClient;
    private AirtablePluginConfigService configService;
    private AirtableRecordsService service;
    private AirtablePluginConfig config;

    @BeforeEach
    void setUp() {
        apiClient = mock(AirtableApiClient.class);
        configService = mock(AirtablePluginConfigService.class);
        service = new AirtableRecordsService(apiClient, configService);
        config = AirtablePluginConfig.builder()
                .enabled(true)
                .apiToken("token")
                .baseId("appBase")
                .defaultTable("Tasks")
                .defaultView("Grid")
                .defaultMaxRecords(20)
                .allowWrite(true)
                .allowDelete(true)
                .typecast(false)
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldListRecordsUsingConfiguredDefaults() {
        when(apiClient.listRecords("Tasks", "Grid", null, 20, List.of(), null, null))
                .thenReturn(new AirtableApiClient.AirtableListResponse(
                        List.of(new AirtableApiClient.AirtableRecord(
                                "rec123",
                                "2026-04-04T00:00:00.000Z",
                                Map.of("Name", "Alice"))),
                        null));

        ToolResult result = service.listRecords(null, null, null, null, List.of(), null, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("rec123"));
        verify(apiClient).listRecords("Tasks", "Grid", null, 20, List.of(), null, null);
    }

    @Test
    void shouldCreateRecordWhenWriteIsAllowed() {
        when(apiClient.createRecord("Tasks", Map.of("Name", "Alice"), false))
                .thenReturn(new AirtableApiClient.AirtableRecord(
                        "rec999",
                        "2026-04-04T00:00:00.000Z",
                        Map.of("Name", "Alice")));

        ToolResult result = service.createRecord(null, Map.of("Name", "Alice"), Optional.empty());

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Created Airtable record rec999"));
        verify(apiClient).createRecord("Tasks", Map.of("Name", "Alice"), false);
    }

    @Test
    void shouldRequireTableWhenNoDefaultExists() {
        config.setDefaultTable("");

        ToolResult result = service.getRecord(null, "rec123");

        assertFalse(result.isSuccess());
        assertEquals("Airtable table is required. Configure a default table or pass the table parameter.",
                result.getError());
    }

    @Test
    void shouldRejectDeleteWhenDisabled() {
        config.setAllowDelete(false);

        ToolResult result = service.deleteRecord(null, "rec123");

        assertFalse(result.isSuccess());
        assertEquals(ToolFailureKind.POLICY_DENIED, result.getFailureKind());
        assertEquals("Airtable delete is disabled in plugin settings", result.getError());
    }
}
