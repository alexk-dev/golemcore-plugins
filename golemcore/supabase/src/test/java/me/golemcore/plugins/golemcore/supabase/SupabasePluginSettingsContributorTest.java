package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiClient;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupabasePluginSettingsContributorTest {

    private SupabasePluginConfigService configService;
    private SupabaseApiClient apiClient;
    private SupabasePluginSettingsContributor contributor;
    private SupabasePluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(SupabasePluginConfigService.class);
        apiClient = mock(SupabaseApiClient.class);
        contributor = new SupabasePluginSettingsContributor(configService, apiClient);
        config = SupabasePluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSafeDefaults() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("", section.getValues().get("projectUrl"));
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals(SupabasePluginConfig.DEFAULT_SCHEMA, section.getValues().get("defaultSchema"));
        assertEquals(SupabasePluginConfig.DEFAULT_SELECT, section.getValues().get("defaultSelect"));
        assertEquals(SupabasePluginConfig.DEFAULT_LIMIT, section.getValues().get("defaultLimit"));
        assertEquals(1, section.getActions().size());
        assertEquals("test-connection", section.getActions().getFirst().getActionId());
    }

    @Test
    void shouldRoundTripSavedValuesWithoutOverwritingBlankSecret() {
        SupabasePluginConfig initialConfig = SupabasePluginConfig.builder()
                .apiKey("existing-secret")
                .build();
        initialConfig.normalize();
        SupabasePluginConfig persistedConfig = SupabasePluginConfig.builder()
                .enabled(true)
                .projectUrl("https://project.supabase.co")
                .apiKey("existing-secret")
                .defaultSchema("public")
                .defaultTable("tasks")
                .defaultSelect("id,name")
                .defaultLimit(15)
                .allowWrite(true)
                .allowDelete(false)
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("projectUrl", "https://project.supabase.co");
        values.put("apiKey", "");
        values.put("defaultSchema", "public");
        values.put("defaultTable", "tasks");
        values.put("defaultSelect", "id,name");
        values.put("defaultLimit", 15);
        values.put("allowWrite", true);
        values.put("allowDelete", false);

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<SupabasePluginConfig> captor = ArgumentCaptor.forClass(SupabasePluginConfig.class);
        verify(configService).save(captor.capture());
        SupabasePluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getApiKey());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("tasks", section.getValues().get("defaultTable"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setProjectUrl("https://project.supabase.co");
        config.setApiKey("token");
        config.setDefaultTable("tasks");
        when(apiClient.selectRows("tasks", "public", "*", 1, null, null, Optional.empty(), Map.of()))
                .thenReturn(List.of());

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertTrue(result.getMessage().contains("Connected to Supabase"));
        verify(apiClient).selectRows("tasks", "public", "*", 1, null, null, Optional.empty(), Map.of());
    }

    @Test
    void shouldReturnErrorWhenProjectUrlIsMissing() {
        config.setApiKey("token");
        config.setDefaultTable("tasks");

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Supabase project URL is not configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionFails() {
        config.setProjectUrl("https://project.supabase.co");
        config.setApiKey("token");
        config.setDefaultTable("tasks");
        when(apiClient.selectRows("tasks", "public", "*", 1, null, null, Optional.empty(), Map.of()))
                .thenThrow(new SupabaseTransportException("Supabase transport failed: timeout", null));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: Supabase transport failed: timeout", result.getMessage());
    }
}
