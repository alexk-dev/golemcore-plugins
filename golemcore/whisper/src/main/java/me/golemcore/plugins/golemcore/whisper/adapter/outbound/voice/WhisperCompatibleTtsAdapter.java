package me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice;

/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.model.AudioFormat;
import me.golemcore.plugin.api.extension.port.outbound.VoicePort;
import me.golemcore.plugin.api.extension.spi.TtsProvider;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.whisper.WhisperPluginConfig;
import me.golemcore.plugins.golemcore.whisper.WhisperPluginConfigService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhisperCompatibleTtsAdapter implements TtsProvider {

    private static final String TTS_PATH = "/v1/audio/speech";
    private static final int MAX_RETRIES = 3;
    private static final String PROVIDER_ID = "golemcore/whisper";
    private static final String LEGACY_PROVIDER_ID = "whisper";

    private final OkHttpClient okHttpClient;
    private final RuntimeConfigService runtimeConfigService;
    private final WhisperPluginConfigService configService;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public java.util.Set<String> getAliases() {
        return java.util.Set.of(LEGACY_PROVIDER_ID);
    }

    @Override
    public boolean isAvailable() {
        return runtimeConfigService.isVoiceEnabled() && isHealthy();
    }

    @Override
    public CompletableFuture<byte[]> synthesize(String text, VoicePort.VoiceConfig config) {
        return CompletableFuture.completedFuture(doSynthesize(text, config));
    }

    public byte[] doSynthesize(String text, VoicePort.VoiceConfig requestConfig) {
        WhisperPluginConfig config = configService.getConfig();
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Whisper TTS URL is not configured");
        }

        String voiceId = defaultIfBlank(requestConfig.voiceId(), config.getVoiceId());
        if (voiceId == null || voiceId.isBlank()) {
            throw new IllegalStateException("Whisper TTS voice is not configured");
        }

        String modelId = defaultIfBlank(requestConfig.modelId(), config.getTtsModelId());
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("Whisper TTS model is not configured");
        }

        float speed = requestConfig.speed() > 0 ? requestConfig.speed() : config.getSpeed();
        AudioFormat outputFormat = requestConfig.outputFormat() != null ? requestConfig.outputFormat()
                : AudioFormat.MP3;
        String responseFormat = resolveResponseFormat(outputFormat);

        log.info("[WhisperTTS] TTS request: {} chars, voice={}, model={}, format={}, speed={}, url={}",
                text.length(), voiceId, modelId, responseFormat, speed, baseUrl);

        try {
            String jsonBody = objectMapper.writeValueAsString(new TtsRequest(
                    modelId,
                    text,
                    voiceId,
                    responseFormat,
                    speed));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(getTtsUrl(baseUrl))
                    .header("Accept", outputFormat.getMimeType())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")));

            String apiKey = config.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = requestBuilder.build();
            long startTime = System.currentTimeMillis();
            return executeWithRetry(request, text.length(), startTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Whisper TTS interrupted", e);
        } catch (UncheckedIOException e) {
            log.error("[WhisperTTS] TTS network error: {}", e.getMessage(), e);
            throw new UncheckedIOException("Whisper synthesis failed: " + e.getMessage(), e.getCause());
        } catch (IOException e) {
            log.error("[WhisperTTS] TTS network error: {}", e.getMessage(), e);
            throw new UncheckedIOException("Whisper synthesis failed: " + e.getMessage(), e);
        }
    }

    public boolean isHealthy() {
        String baseUrl = configService.getConfig().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            Request request = new Request.Builder()
                    .url(normalizeBaseUrl(baseUrl) + "/health")
                    .get()
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) { // NOSONAR - best-effort health check
            log.debug("[WhisperTTS] Health check failed: {}", e.getMessage());
            return false;
        }
    }

    protected String getTtsUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl) + TTS_PATH;
    }

    protected void sleepBeforeRetry(long backoffMs) throws InterruptedException {
        Thread.sleep(backoffMs);
    }

    @SuppressWarnings("PMD.CloseResource") // ResponseBody is closed when Response is closed in try-with-resources
    private byte[] executeWithRetry(Request request, int textLength, long startTime)
            throws IOException, InterruptedException {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try (Response response = okHttpClient.newCall(request).execute()) {
                ResponseBody body = response.body();

                if (!response.isSuccessful()) {
                    if (isRetryableError(response.code()) && attempt < MAX_RETRIES - 1) {
                        attempt++;
                        long backoffMs = (long) Math.pow(2, attempt) * 1000;
                        log.info("[WhisperTTS] Retrying after {} (attempt {}/{}), backoff={}ms",
                                response.code(), attempt, MAX_RETRIES, backoffMs);
                        sleepBeforeRetry(backoffMs);
                        continue;
                    }
                    String errorBody = body != null ? body.string() : "";
                    throw new IllegalStateException(
                            String.format("Whisper TTS error (HTTP %d): %s", response.code(), errorBody));
                }

                if (body == null) {
                    throw new IllegalStateException("Whisper TTS returned empty body");
                }

                byte[] audioBytes = body.bytes();
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[WhisperTTS] TTS success: {} chars -> {} bytes, {}ms",
                        textLength, audioBytes.length, elapsed);
                return audioBytes;
            }
        }

        throw new IllegalStateException("Whisper TTS failed after " + MAX_RETRIES + " attempts");
    }

    private String resolveResponseFormat(AudioFormat format) {
        return switch (format) {
        case OGG_OPUS -> "opus";
        case MP3 -> "mp3";
        case WAV -> "wav";
        case PCM_16K, PCM_44K -> "pcm";
        };
    }

    private boolean isRetryableError(int code) {
        return code == 429 || code == 500 || code == 503 || code == 504;
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    record TtsRequest(
            String model,
            String input,
            String voice,
            @JsonProperty("response_format") String responseFormat,
            float speed) {
    }
}
