package me.golemcore.plugins.golemcore.elevenlabs;

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

class ElevenLabsPluginSettingsContributorTest {

    private RuntimeConfigService runtimeConfigService;
    private ElevenLabsPluginConfigService configService;
    private ElevenLabsPluginSettingsContributor contributor;
    private ElevenLabsPluginConfig config;

    @BeforeEach
    void setUp() {
        runtimeConfigService = mock(RuntimeConfigService.class);
        configService = mock(ElevenLabsPluginConfigService.class);
        contributor = new ElevenLabsPluginSettingsContributor(runtimeConfigService, configService);
        config = ElevenLabsPluginConfig.builder()
                .apiKey("existing-key")
                .voiceId("voice-1")
                .ttsModelId("tts-1")
                .sttModelId("stt-1")
                .speed(1.2f)
                .build();
        when(configService.getConfig()).thenReturn(config);
        when(runtimeConfigService.getSttProvider()).thenReturn("golemcore/elevenlabs");
        when(runtimeConfigService.getTtsProvider()).thenReturn("golemcore/elevenlabs");
    }

    @Test
    void shouldExposeSectionFromPluginOwnedConfigWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("ElevenLabs", section.getTitle());
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("voice-1", section.getValues().get("voiceId"));
        assertEquals("tts-1", section.getValues().get("ttsModelId"));
        assertEquals("stt-1", section.getValues().get("sttModelId"));
        assertEquals(1.2f, section.getValues().get("speed"));
        assertTrue(section.getBlocks().getFirst().getText().contains("active"));
    }

    @Test
    void shouldPreserveExistingApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "apiKey", "",
                "voiceId", "voice-2",
                "ttsModelId", "tts-2",
                "sttModelId", "stt-2",
                "speed", "1.4"));

        ArgumentCaptor<ElevenLabsPluginConfig> captor = ArgumentCaptor.forClass(ElevenLabsPluginConfig.class);
        verify(configService).save(captor.capture());
        ElevenLabsPluginConfig saved = captor.getValue();

        assertEquals("existing-key", saved.getApiKey());
        assertEquals("voice-2", saved.getVoiceId());
        assertEquals("tts-2", saved.getTtsModelId());
        assertEquals("stt-2", saved.getSttModelId());
        assertEquals(1.4f, saved.getSpeed());
    }
}
