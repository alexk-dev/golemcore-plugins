package me.golemcore.plugins.golemcore.whisper;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhisperPluginConfigServiceTest {

    private PluginConfigurationService pluginConfigurationService;
    private WhisperPluginConfigService service;

    @BeforeEach
    void setUp() {
        pluginConfigurationService = mock(PluginConfigurationService.class);
        service = new WhisperPluginConfigService(pluginConfigurationService);
    }

    @Test
    void shouldReadStoredPluginConfig() {
        when(pluginConfigurationService.hasPluginConfig(WhisperPluginConfigService.PLUGIN_ID)).thenReturn(true);
        when(pluginConfigurationService.getPluginConfig(WhisperPluginConfigService.PLUGIN_ID)).thenReturn(Map.of(
                "baseUrl", "http://localhost:5092",
                "apiKey", "plugin-key",
                "voiceId", "nova",
                "ttsModelId", "tts-local",
                "speed", 1.4f));

        WhisperPluginConfig config = service.getConfig();

        assertEquals("http://localhost:5092", config.getBaseUrl());
        assertEquals("plugin-key", config.getApiKey());
        assertEquals("nova", config.getVoiceId());
        assertEquals("tts-local", config.getTtsModelId());
        assertEquals(1.4f, config.getSpeed());
    }

    @Test
    void shouldReturnEmptyConfigWhenPluginConfigMissing() {
        when(pluginConfigurationService.hasPluginConfig(WhisperPluginConfigService.PLUGIN_ID)).thenReturn(false);

        WhisperPluginConfig config = service.getConfig();

        assertNull(config.getBaseUrl());
        assertNull(config.getApiKey());
        assertEquals(WhisperPluginConfig.DEFAULT_VOICE_ID, config.getVoiceId());
        assertEquals(WhisperPluginConfig.DEFAULT_TTS_MODEL_ID, config.getTtsModelId());
        assertEquals(WhisperPluginConfig.DEFAULT_SPEED, config.getSpeed());
    }

    @Test
    void shouldPersistNormalizedPluginConfig() {
        WhisperPluginConfig config = WhisperPluginConfig.builder()
                .baseUrl(" http://localhost:5092/ ")
                .apiKey("   ")
                .voiceId("  ")
                .ttsModelId(null)
                .speed(0f)
                .build();

        service.save(config);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginConfigurationService).savePluginConfig(
                org.mockito.Mockito.eq(WhisperPluginConfigService.PLUGIN_ID),
                captor.capture());

        Map<String, Object> saved = captor.getValue();
        assertEquals("http://localhost:5092/", saved.get("baseUrl"));
        assertNull(saved.get("apiKey"));
        assertEquals(WhisperPluginConfig.DEFAULT_VOICE_ID, saved.get("voiceId"));
        assertEquals(WhisperPluginConfig.DEFAULT_TTS_MODEL_ID, saved.get("ttsModelId"));
        assertEquals(WhisperPluginConfig.DEFAULT_SPEED, saved.get("speed"));
    }
}
