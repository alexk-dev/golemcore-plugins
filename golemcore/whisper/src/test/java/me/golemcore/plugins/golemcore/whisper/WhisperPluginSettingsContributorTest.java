package me.golemcore.plugins.golemcore.whisper;

import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhisperPluginSettingsContributorTest {

    private RuntimeConfigService runtimeConfigService;
    private WhisperPluginConfigService configService;
    private WhisperPluginSettingsContributor contributor;
    private WhisperPluginConfig config;

    @BeforeEach
    void setUp() {
        runtimeConfigService = mock(RuntimeConfigService.class);
        configService = mock(WhisperPluginConfigService.class);
        contributor = new WhisperPluginSettingsContributor(runtimeConfigService, configService);
        config = WhisperPluginConfig.builder()
                .baseUrl("http://localhost:5092")
                .apiKey("existing-key")
                .voiceId("nova")
                .ttsModelId("tts-local")
                .speed(1.3f)
                .build();
        when(configService.getConfig()).thenReturn(config);
        when(runtimeConfigService.getSttProvider()).thenReturn("golemcore/whisper");
        when(runtimeConfigService.getTtsProvider()).thenReturn("golemcore/whisper");
    }

    @Test
    void shouldExposeSectionFromPluginOwnedConfigWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("Whisper", section.getTitle());
        assertEquals("http://localhost:5092", section.getValues().get("baseUrl"));
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("nova", section.getValues().get("voiceId"));
        assertEquals("tts-local", section.getValues().get("ttsModelId"));
        assertEquals(1.3f, section.getValues().get("speed"));
        assertTrue(section.getBlocks().getFirst().getText().contains("STT: active"));
        assertTrue(section.getBlocks().getFirst().getText().contains("TTS: active"));
    }

    @Test
    void shouldPreserveExistingApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "baseUrl", "http://localhost:6006",
                "apiKey", "",
                "voiceId", "ash",
                "ttsModelId", "tts-next",
                "speed", "1.4"));

        ArgumentCaptor<WhisperPluginConfig> captor = ArgumentCaptor.forClass(WhisperPluginConfig.class);
        verify(configService).save(captor.capture());
        WhisperPluginConfig saved = captor.getValue();

        assertEquals("http://localhost:6006", saved.getBaseUrl());
        assertEquals("existing-key", saved.getApiKey());
        assertEquals("ash", saved.getVoiceId());
        assertEquals("tts-next", saved.getTtsModelId());
        assertEquals(1.4f, saved.getSpeed());
    }
}
