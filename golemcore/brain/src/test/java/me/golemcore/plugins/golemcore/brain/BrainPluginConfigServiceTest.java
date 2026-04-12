package me.golemcore.plugins.golemcore.brain;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrainPluginConfigServiceTest {

    @Test
    void shouldLoadAndNormalizeConfig() {
        PluginConfigurationService pluginConfigurationService = mock(PluginConfigurationService.class);
        when(pluginConfigurationService.getPluginConfig(BrainPluginConfigService.PLUGIN_ID)).thenReturn(Map.of(
                "enabled", true,
                "baseUrl", "https://brain.example/",
                "apiToken", "token",
                "defaultSpaceSlug", "docs",
                "allowWrite", true,
                "defaultIntellisearchLimit", 500));

        BrainPluginConfig config = new BrainPluginConfigService(pluginConfigurationService).getConfig();

        assertTrue(config.getEnabled());
        assertEquals("https://brain.example", config.getBaseUrl());
        assertEquals("docs", config.getDefaultSpaceSlug());
        assertEquals(20, config.getDefaultIntellisearchLimit());
        assertTrue(config.getAllowWrite());
    }

    @Test
    void shouldPersistNormalizedConfig() {
        PluginConfigurationService pluginConfigurationService = mock(PluginConfigurationService.class);
        BrainPluginConfigService service = new BrainPluginConfigService(pluginConfigurationService);
        BrainPluginConfig config = BrainPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://brain.example/")
                .apiToken("token")
                .defaultSpaceSlug("docs")
                .allowWrite(false)
                .defaultIntellisearchLimit(0)
                .build();

        service.save(config);

        verify(pluginConfigurationService).savePluginConfig(eq(BrainPluginConfigService.PLUGIN_ID),
                org.mockito.Mockito.argThat(saved -> {
                    Object baseUrl = saved.get("baseUrl");
                    Object defaultLimit = saved.get("defaultIntellisearchLimit");
                    Object allowWrite = saved.get("allowWrite");
                    return "https://brain.example".equals(baseUrl)
                            && Integer.valueOf(5).equals(defaultLimit)
                            && Boolean.FALSE.equals(allowWrite);
                }));
    }

    @Test
    void shouldDefaultDisabledConfig() {
        PluginConfigurationService pluginConfigurationService = mock(PluginConfigurationService.class);
        when(pluginConfigurationService.getPluginConfig(BrainPluginConfigService.PLUGIN_ID)).thenReturn(Map.of());

        BrainPluginConfig config = new BrainPluginConfigService(pluginConfigurationService).getConfig();

        assertFalse(config.getEnabled());
        assertEquals("default", config.getDefaultSpaceSlug());
        assertEquals(5, config.getDefaultIntellisearchLimit());
    }
}
