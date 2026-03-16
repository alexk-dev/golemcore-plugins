package me.golemcore.plugins.golemcore.elevenlabs;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElevenLabsPluginConfigServiceTest {

    private PluginConfigurationService pluginConfigurationService;
    private RuntimeConfigService runtimeConfigService;
    private ElevenLabsPluginConfigService service;

    @BeforeEach
    void setUp() {
        pluginConfigurationService = mock(PluginConfigurationService.class);
        runtimeConfigService = mock(RuntimeConfigService.class);
        service = new ElevenLabsPluginConfigService(pluginConfigurationService, runtimeConfigService);
    }

    @Test
    void shouldReadStoredPluginConfigBeforeLegacyRuntimeVoiceConfig() {
        when(pluginConfigurationService.hasPluginConfig(ElevenLabsPluginConfigService.PLUGIN_ID)).thenReturn(true);
        when(pluginConfigurationService.getPluginConfig(ElevenLabsPluginConfigService.PLUGIN_ID)).thenReturn(Map.of(
                "apiKey", "plugin-key",
                "voiceId", "plugin-voice",
                "ttsModelId", "plugin-tts",
                "sttModelId", "plugin-stt",
                "speed", 1.3f));

        ElevenLabsPluginConfig config = service.getConfig();

        assertEquals("plugin-key", config.getApiKey());
        assertEquals("plugin-voice", config.getVoiceId());
        assertEquals("plugin-tts", config.getTtsModelId());
        assertEquals("plugin-stt", config.getSttModelId());
        assertEquals(1.3f, config.getSpeed());
    }

    @Test
    void shouldFallbackToLegacyRuntimeVoiceConfigWhenPluginConfigIsMissing() {
        when(pluginConfigurationService.hasPluginConfig(ElevenLabsPluginConfigService.PLUGIN_ID)).thenReturn(false);
        when(runtimeConfigService.getVoiceApiKey()).thenReturn("legacy-key");
        when(runtimeConfigService.getVoiceId()).thenReturn("legacy-voice");
        when(runtimeConfigService.getTtsModelId()).thenReturn("legacy-tts");
        when(runtimeConfigService.getSttModelId()).thenReturn("legacy-stt");
        when(runtimeConfigService.getVoiceSpeed()).thenReturn(1.4f);

        ElevenLabsPluginConfig config = service.getConfig();

        assertEquals("legacy-key", config.getApiKey());
        assertEquals("legacy-voice", config.getVoiceId());
        assertEquals("legacy-tts", config.getTtsModelId());
        assertEquals("legacy-stt", config.getSttModelId());
        assertEquals(1.4f, config.getSpeed());
    }

    @Test
    void shouldPersistNormalizedPluginConfig() {
        ElevenLabsPluginConfig config = ElevenLabsPluginConfig.builder()
                .apiKey("test-key")
                .voiceId("  ")
                .ttsModelId(null)
                .sttModelId("")
                .speed(0f)
                .build();

        service.save(config);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pluginConfigurationService).savePluginConfig(
                org.mockito.Mockito.eq(ElevenLabsPluginConfigService.PLUGIN_ID),
                captor.capture());

        Map<String, Object> saved = captor.getValue();
        assertEquals("test-key", saved.get("apiKey"));
        assertEquals(ElevenLabsPluginConfig.DEFAULT_VOICE_ID, saved.get("voiceId"));
        assertEquals(ElevenLabsPluginConfig.DEFAULT_TTS_MODEL_ID, saved.get("ttsModelId"));
        assertEquals(ElevenLabsPluginConfig.DEFAULT_STT_MODEL_ID, saved.get("sttModelId"));
        assertEquals(ElevenLabsPluginConfig.DEFAULT_SPEED, saved.get("speed"));
    }

    @Test
    void shouldTreatBlankLegacyApiKeyAsMissing() {
        when(pluginConfigurationService.hasPluginConfig(ElevenLabsPluginConfigService.PLUGIN_ID)).thenReturn(false);
        when(runtimeConfigService.getVoiceApiKey()).thenReturn("   ");
        when(runtimeConfigService.getVoiceId()).thenReturn(ElevenLabsPluginConfig.DEFAULT_VOICE_ID);
        when(runtimeConfigService.getTtsModelId()).thenReturn(ElevenLabsPluginConfig.DEFAULT_TTS_MODEL_ID);
        when(runtimeConfigService.getSttModelId()).thenReturn(ElevenLabsPluginConfig.DEFAULT_STT_MODEL_ID);
        when(runtimeConfigService.getVoiceSpeed()).thenReturn(ElevenLabsPluginConfig.DEFAULT_SPEED);

        ElevenLabsPluginConfig config = service.getConfig();

        assertNull(config.getApiKey());
    }
}
