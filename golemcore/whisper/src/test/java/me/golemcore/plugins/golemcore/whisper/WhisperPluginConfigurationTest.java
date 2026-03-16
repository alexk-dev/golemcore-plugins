package me.golemcore.plugins.golemcore.whisper;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice.WhisperCompatibleSttAdapter;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class WhisperPluginConfigurationTest {

    @Test
    void shouldCreatePluginLocalOkHttpClientForAdapter() {
        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RuntimeConfigService.class, () -> runtimeConfigService);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(WhisperPluginConfiguration.class);
            context.refresh();

            OkHttpClient okHttpClient = context.getBean(OkHttpClient.class);
            WhisperCompatibleSttAdapter adapter = context.getBean(WhisperCompatibleSttAdapter.class);

            assertNotNull(context.getBean(WhisperPluginSettingsContributor.class));
            assertSame(okHttpClient, ReflectionTestUtils.getField(adapter, "okHttpClient"));
        }
    }
}
