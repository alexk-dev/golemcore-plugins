package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsidianPluginSettingsContributorTest {

    private ObsidianPluginConfigService configService;
    private ObsidianPluginSettingsContributor contributor;
    private ObsidianPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(ObsidianPluginConfigService.class);
        contributor = new ObsidianPluginSettingsContributor(configService);
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
    }

    @Test
    void shouldRoundTripSavedPolicyFlagsThroughGetSection() {
        config.setEnabled(true);
        config.setBaseUrl("https://127.0.0.1:27124");
        config.setApiKey("existing-secret");
        config.setTimeoutMs(45_000);
        config.setAllowInsecureTls(true);
        config.setDefaultSearchContextLength(120);
        config.setMaxReadChars(8_000);

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
}
