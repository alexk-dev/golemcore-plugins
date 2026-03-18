package me.golemcore.plugins.golemcore.slack;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackPluginConfigServiceTest {

    private PluginConfigurationService pluginConfigurationService;
    private SlackPluginConfigService service;

    @BeforeEach
    void setUp() {
        pluginConfigurationService = mock(PluginConfigurationService.class);
        service = new SlackPluginConfigService(pluginConfigurationService);
    }

    @Test
    void shouldIgnoreLegacyConfiguredFieldWhenReadingStoredPluginConfig() {
        when(pluginConfigurationService.getPluginConfig(SlackPluginConfigService.PLUGIN_ID)).thenReturn(Map.of(
                "enabled", true,
                "botToken", "xoxb-test",
                "appToken", "xapp-test",
                "replyInThread", false,
                "configured", true,
                "allowedUserIds", java.util.List.of("U123"),
                "allowedChannelIds", java.util.List.of("C123")));

        SlackPluginConfig config = service.getConfig();

        assertEquals("xoxb-test", config.getBotToken());
        assertEquals("xapp-test", config.getAppToken());
        assertFalse(Boolean.TRUE.equals(config.getReplyInThread()));
        assertEquals(java.util.List.of("U123"), config.getAllowedUserIds());
        assertEquals(java.util.List.of("C123"), config.getAllowedChannelIds());
    }

    @Test
    void shouldPersistNormalizedPluginConfigWithoutComputedFields() {
        SlackPluginConfig config = SlackPluginConfig.builder()
                .enabled(true)
                .botToken(" xoxb-token ")
                .appToken(" xapp-token ")
                .replyInThread(null)
                .allowedUserIds(java.util.List.of(" U123 ", "", "U123"))
                .allowedChannelIds(java.util.List.of(" C123 ", "C123"))
                .build();

        service.save(config);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginConfigurationService).savePluginConfig(eq(SlackPluginConfigService.PLUGIN_ID), captor.capture());

        Map<String, Object> saved = captor.getValue();
        assertEquals("xoxb-token", saved.get("botToken"));
        assertEquals("xapp-token", saved.get("appToken"));
        assertEquals(true, saved.get("enabled"));
        assertEquals(true, saved.get("replyInThread"));
        assertEquals(java.util.List.of("U123"), saved.get("allowedUserIds"));
        assertEquals(java.util.List.of("C123"), saved.get("allowedChannelIds"));
        assertFalse(saved.containsKey("configured"));
    }
}
