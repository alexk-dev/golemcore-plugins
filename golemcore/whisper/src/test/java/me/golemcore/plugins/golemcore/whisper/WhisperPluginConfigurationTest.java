package me.golemcore.plugins.golemcore.whisper;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice.WhisperCompatibleSttAdapter;
import me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice.WhisperCompatibleTtsAdapter;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class WhisperPluginConfigurationTest {

    @Test
    void shouldCreatePluginLocalOkHttpClientForVoiceAdapters() {
        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);
        PluginConfigurationService pluginConfigurationService = mock(PluginConfigurationService.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuntimeConfigService.class, () -> runtimeConfigService);
            context.registerBean(PluginConfigurationService.class, () -> pluginConfigurationService);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(WhisperPluginConfiguration.class);
            context.refresh();

            OkHttpClient okHttpClient = context.getBean(OkHttpClient.class);
            WhisperCompatibleSttAdapter sttAdapter = context.getBean(WhisperCompatibleSttAdapter.class);
            WhisperCompatibleTtsAdapter ttsAdapter = context.getBean(WhisperCompatibleTtsAdapter.class);

            assertNotNull(context.getBean(WhisperPluginSettingsContributor.class));
            assertSame(okHttpClient, ReflectionTestUtils.getField(sttAdapter, "okHttpClient"));
            assertSame(okHttpClient, ReflectionTestUtils.getField(ttsAdapter, "okHttpClient"));
        }
    }
}
