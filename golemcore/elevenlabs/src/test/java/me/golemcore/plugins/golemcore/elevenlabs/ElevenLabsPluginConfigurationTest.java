package me.golemcore.plugins.golemcore.elevenlabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.elevenlabs.adapter.outbound.voice.ElevenLabsAdapter;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElevenLabsPluginConfigurationTest {

    @Test
    void shouldCreatePluginLocalOkHttpClientForAdapter() {
        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);
        when(runtimeConfigService.isVoiceEnabled()).thenReturn(true);
        when(runtimeConfigService.getSttProvider()).thenReturn("golemcore/elevenlabs");
        when(runtimeConfigService.getTtsProvider()).thenReturn("golemcore/elevenlabs");

        PluginConfigurationService pluginConfigurationService = mock(PluginConfigurationService.class);
        when(pluginConfigurationService.hasPluginConfig("golemcore/elevenlabs")).thenReturn(true);
        when(pluginConfigurationService.getPluginConfig("golemcore/elevenlabs")).thenReturn(Map.of());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuntimeConfigService.class, () -> runtimeConfigService);
            context.registerBean(PluginConfigurationService.class, () -> pluginConfigurationService);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(ElevenLabsPluginConfiguration.class);
            context.refresh();

            OkHttpClient okHttpClient = context.getBean(OkHttpClient.class);
            ElevenLabsAdapter adapter = context.getBean(ElevenLabsAdapter.class);

            assertNotNull(context.getBean(ElevenLabsPluginSettingsContributor.class));
            assertSame(okHttpClient, ReflectionTestUtils.getField(adapter, "okHttpClient"));
        }
    }
}
