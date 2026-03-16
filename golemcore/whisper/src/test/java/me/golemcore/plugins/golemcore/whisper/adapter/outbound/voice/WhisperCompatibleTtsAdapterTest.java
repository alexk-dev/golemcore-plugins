package me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.bot.testsupport.http.OkHttpMockEngine;
import me.golemcore.plugin.api.extension.model.AudioFormat;
import me.golemcore.plugin.api.extension.port.outbound.VoicePort;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.whisper.WhisperPluginConfig;
import me.golemcore.plugins.golemcore.whisper.WhisperPluginConfigService;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhisperCompatibleTtsAdapterTest {

    private static final String BASE_URL = "http://mock.whisper.local";

    private OkHttpMockEngine httpEngine;
    private WhisperCompatibleTtsAdapter adapter;
    private RuntimeConfigService runtimeConfigService;
    private WhisperPluginConfigService configService;
    private WhisperPluginConfig config;

    @BeforeEach
    void setUp() {
        httpEngine = new OkHttpMockEngine();

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(httpEngine)
                .build();

        runtimeConfigService = mock(RuntimeConfigService.class);
        when(runtimeConfigService.isVoiceEnabled()).thenReturn(true);
        configService = mock(WhisperPluginConfigService.class);
        config = WhisperPluginConfig.builder()
                .baseUrl(BASE_URL)
                .apiKey(null)
                .voiceId(WhisperPluginConfig.DEFAULT_VOICE_ID)
                .ttsModelId(WhisperPluginConfig.DEFAULT_TTS_MODEL_ID)
                .speed(WhisperPluginConfig.DEFAULT_SPEED)
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);

        adapter = new WhisperCompatibleTtsAdapter(client, runtimeConfigService, configService, new ObjectMapper()) {
            @Override
            protected void sleepBeforeRetry(long backoffMs) {
                // No-op in tests to avoid real backoff delays.
            }
        };
    }

    @Test
    void shouldSynthesizeSuccessfullyWithConfiguredDefaults() {
        byte[] expected = "AUDIO".getBytes(StandardCharsets.UTF_8);
        httpEngine.enqueueBytes(200, expected, "audio/mpeg");

        byte[] result = adapter.doSynthesize("Hello world", VoicePort.VoiceConfig.defaultConfig());

        assertArrayEquals(expected, result);
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertEquals("/v1/audio/speech", request.target());
        assertTrue(request.body().contains("\"model\":\"gpt-4o-mini-tts\""));
        assertTrue(request.body().contains("\"voice\":\"alloy\""));
        assertTrue(request.body().contains("\"response_format\":\"mp3\""));
        assertTrue(request.body().contains("\"speed\":1.0"));
    }

    @Test
    void shouldUseRequestConfigOverrides() {
        httpEngine.enqueueBytes(200, new byte[] { 1, 2, 3 }, "audio/wav");

        adapter.doSynthesize("Override", new VoicePort.VoiceConfig("nova", "tts-override", 1.4f, AudioFormat.WAV));

        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertTrue(request.body().contains("\"model\":\"tts-override\""));
        assertTrue(request.body().contains("\"voice\":\"nova\""));
        assertTrue(request.body().contains("\"response_format\":\"wav\""));
        assertTrue(request.body().contains("\"speed\":1.4"));
        assertEquals("audio/wav", request.headers().get("Accept"));
    }

    @Test
    void shouldMapOggOutputToOpusResponseFormat() {
        httpEngine.enqueueBytes(200, new byte[] { 9 }, "audio/ogg");

        adapter.doSynthesize("Opus", new VoicePort.VoiceConfig(null, null, 0f, AudioFormat.OGG_OPUS));

        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertTrue(request.body().contains("\"response_format\":\"opus\""));
        assertEquals("audio/ogg", request.headers().get("Accept"));
    }

    @Test
    void shouldSendAuthHeaderWhenApiKeyConfigured() {
        config.setApiKey("sk-test-key");
        httpEngine.enqueueBytes(200, new byte[] { 1 }, "audio/mpeg");

        adapter.doSynthesize("Auth", VoicePort.VoiceConfig.defaultConfig());

        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertEquals("Bearer sk-test-key", request.headers().get("Authorization"));
    }

    @Test
    void shouldNotSendAuthHeaderWhenApiKeyBlank() {
        config.setApiKey("   ");
        httpEngine.enqueueBytes(200, new byte[] { 1 }, "audio/mpeg");

        adapter.doSynthesize("Auth", VoicePort.VoiceConfig.defaultConfig());

        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertNull(request.headers().get("Authorization"));
    }

    @Test
    void shouldRetryOnServerError() {
        byte[] expected = new byte[] { 7, 8, 9 };
        httpEngine.enqueueText(500, "Server error", "text/plain");
        httpEngine.enqueueBytes(200, expected, "audio/mpeg");

        byte[] result = adapter.doSynthesize("Retry", VoicePort.VoiceConfig.defaultConfig());

        assertArrayEquals(expected, result);
        assertEquals(2, httpEngine.getRequestCount());
    }

    @Test
    void shouldFailAfterMaxRetries() {
        httpEngine.enqueueText(503, "Unavailable", "text/plain");
        httpEngine.enqueueText(503, "Unavailable", "text/plain");
        httpEngine.enqueueText(503, "Unavailable", "text/plain");

        Exception ex = assertThrows(Exception.class,
                () -> adapter.doSynthesize("Retry", VoicePort.VoiceConfig.defaultConfig()));
        assertTrue(ex.getMessage().contains("503") || ex.getMessage().contains("failed"));
        assertEquals(3, httpEngine.getRequestCount());
    }

    @Test
    void shouldNotRetryOnClientError() {
        httpEngine.enqueueText(400, "Bad request", "text/plain");

        Exception ex = assertThrows(Exception.class,
                () -> adapter.doSynthesize("Bad request", VoicePort.VoiceConfig.defaultConfig()));
        assertTrue(ex.getMessage().contains("400"));
        assertEquals(1, httpEngine.getRequestCount());
    }

    @Test
    void shouldThrowWhenUrlNotConfigured() {
        config.setBaseUrl("");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adapter.doSynthesize("Hello", VoicePort.VoiceConfig.defaultConfig()));
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    void shouldReportHealthy() {
        httpEngine.enqueueText(200, "OK", "text/plain");

        assertTrue(adapter.isHealthy());
        OkHttpMockEngine.CapturedRequest request = httpEngine.takeRequest();
        assertNotNull(request);
        assertEquals("/health", request.target());
    }

    @Test
    void shouldReportUnhealthyWhenUrlEmpty() {
        config.setBaseUrl("");

        assertFalse(adapter.isHealthy());
    }
}
