package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianApiClient;
import me.golemcore.plugins.golemcore.obsidian.support.ObsidianTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsidianPluginSettingsContributorTest {

    private ObsidianPluginConfigService configService;
    private ObsidianApiClient apiClient;
    private ObsidianPluginSettingsContributor contributor;
    private ObsidianPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(ObsidianPluginConfigService.class);
        apiClient = mock(ObsidianApiClient.class);
        contributor = new ObsidianPluginSettingsContributor(configService, apiClient);
        config = ObsidianPluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretAndSafeDefaultsFromDefaultConfig() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("http://127.0.0.1:27123", section.getValues().get("baseUrl"));
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals(30_000, section.getValues().get("timeoutMs"));
        assertEquals(false, section.getValues().get("allowInsecureTls"));
        assertEquals(100, section.getValues().get("defaultSearchContextLength"));
        assertEquals(12_000, section.getValues().get("maxReadChars"));
        assertFalse((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertFalse((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowRename"));
        assertEquals(1, section.getActions().size());
        assertEquals("test-connection", section.getActions().getFirst().getActionId());
        assertEquals("Test Connection", section.getActions().getFirst().getLabel());
    }

    @Test
    void shouldRoundTripSavedPolicyFlagsThroughGetSection() {
        ObsidianPluginConfig initialConfig = ObsidianPluginConfig.builder()
                .apiKey("existing-secret")
                .build();
        initialConfig.normalize();
        ObsidianPluginConfig persistedConfig = ObsidianPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://127.0.0.1:27124")
                .apiKey("existing-secret")
                .timeoutMs(45_000)
                .allowInsecureTls(true)
                .defaultSearchContextLength(120)
                .maxReadChars(8_000)
                .allowWrite(true)
                .allowDelete(false)
                .allowMove(true)
                .allowRename(false)
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("baseUrl", "https://127.0.0.1:27124");
        values.put("apiKey", "");
        values.put("timeoutMs", 45_000);
        values.put("allowInsecureTls", true);
        values.put("defaultSearchContextLength", 120);
        values.put("maxReadChars", 8_000);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("allowMove", true);
        values.put("allowRename", false);

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<ObsidianPluginConfig> captor = ArgumentCaptor.forClass(ObsidianPluginConfig.class);
        verify(configService).save(captor.capture());
        ObsidianPluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getApiKey());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertTrue(saved.getAllowMove());
        assertFalse(saved.getAllowRename());

        assertEquals("", section.getValues().get("apiKey"));
        assertTrue((Boolean) section.getValues().get("allowWrite"));
        assertFalse((Boolean) section.getValues().get("allowDelete"));
        assertTrue((Boolean) section.getValues().get("allowMove"));
        assertFalse((Boolean) section.getValues().get("allowRename"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setApiKey("secret");
        when(apiClient.listDirectory("")).thenReturn(List.of("Inbox.md", "Projects/"));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Connected to Obsidian. Vault root returned 2 item(s).", result.getMessage());
        verify(apiClient).listDirectory("");
    }

    @Test
    void shouldReturnErrorWhenApiKeyIsMissing() {
        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Obsidian API key is not configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionTestFails() {
        config.setApiKey("secret");
        when(apiClient.listDirectory("")).thenThrow(new ObsidianTransportException(
                "Obsidian transport failed: timeout",
                new IOException("timeout")));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: Obsidian transport failed: timeout", result.getMessage());
    }
}
