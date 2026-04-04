package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiClient;
import me.golemcore.plugins.golemcore.airtable.support.AirtableTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AirtablePluginSettingsContributorTest {

    private AirtablePluginConfigService configService;
    private AirtableApiClient apiClient;
    private AirtablePluginSettingsContributor contributor;
    private AirtablePluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(AirtablePluginConfigService.class);
        apiClient = mock(AirtableApiClient.class);
        contributor = new AirtablePluginSettingsContributor(configService, apiClient);
        config = AirtablePluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSafeDefaults() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals(AirtablePluginConfig.DEFAULT_API_BASE_URL, section.getValues().get("apiBaseUrl"));
        assertEquals("", section.getValues().get("apiToken"));
        assertEquals("", section.getValues().get("baseId"));
        assertEquals("", section.getValues().get("defaultTable"));
        assertEquals(AirtablePluginConfig.DEFAULT_MAX_RECORDS, section.getValues().get("defaultMaxRecords"));
        assertEquals(1, section.getActions().size());
        assertEquals("test-connection", section.getActions().getFirst().getActionId());
    }

    @Test
    void shouldRoundTripSavedValuesWithoutOverwritingBlankSecret() {
        AirtablePluginConfig initialConfig = AirtablePluginConfig.builder()
                .apiToken("existing-secret")
                .build();
        initialConfig.normalize();
        AirtablePluginConfig persistedConfig = AirtablePluginConfig.builder()
                .enabled(true)
                .apiBaseUrl("https://api.airtable.com")
                .apiToken("existing-secret")
                .baseId("appBase")
                .defaultTable("Tasks")
                .defaultView("Grid")
                .defaultMaxRecords(15)
                .allowWrite(true)
                .allowDelete(false)
                .typecast(true)
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("apiBaseUrl", "https://api.airtable.com");
        values.put("apiToken", "");
        values.put("baseId", "appBase");
        values.put("defaultTable", "Tasks");
        values.put("defaultView", "Grid");
        values.put("defaultMaxRecords", 15);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("typecast", true);

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<AirtablePluginConfig> captor = ArgumentCaptor.forClass(AirtablePluginConfig.class);
        verify(configService).save(captor.capture());
        AirtablePluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getApiToken());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertTrue(saved.getTypecast());
        assertEquals("", section.getValues().get("apiToken"));
        assertEquals("Tasks", section.getValues().get("defaultTable"));
        assertEquals(true, section.getValues().get("allowWrite"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setApiToken("token");
        config.setBaseId("appBase");
        config.setDefaultTable("Tasks");
        when(apiClient.listRecords("Tasks", "", null, 1, List.of(), null, null))
                .thenReturn(new AirtableApiClient.AirtableListResponse(List.of(), null));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertTrue(result.getMessage().contains("Connected to Airtable"));
        verify(apiClient).listRecords("Tasks", "", null, 1, List.of(), null, null);
    }

    @Test
    void shouldReturnErrorWhenTokenIsMissing() {
        config.setBaseId("appBase");
        config.setDefaultTable("Tasks");

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Airtable API token is not configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionFails() {
        config.setApiToken("token");
        config.setBaseId("appBase");
        config.setDefaultTable("Tasks");
        when(apiClient.listRecords("Tasks", "", null, 1, List.of(), null, null))
                .thenThrow(new AirtableTransportException("Airtable transport failed: timeout", null));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: Airtable transport failed: timeout", result.getMessage());
    }
}
