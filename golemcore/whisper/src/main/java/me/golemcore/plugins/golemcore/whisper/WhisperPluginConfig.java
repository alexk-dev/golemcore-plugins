package me.golemcore.plugins.golemcore.whisper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhisperPluginConfig {

    public static final String DEFAULT_VOICE_ID = "alloy";
    public static final String DEFAULT_TTS_MODEL_ID = "gpt-4o-mini-tts";
    public static final float DEFAULT_SPEED = 1.0f;

    private String baseUrl;
    private String apiKey;
    private String voiceId;
    private String ttsModelId;
    private Float speed;

    public void normalize() {
        baseUrl = blankToNull(baseUrl);
        apiKey = blankToNull(apiKey);
        voiceId = defaultIfBlank(voiceId, DEFAULT_VOICE_ID);
        ttsModelId = defaultIfBlank(ttsModelId, DEFAULT_TTS_MODEL_ID);
        speed = speed != null && speed > 0 ? speed : DEFAULT_SPEED;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : fallback;
    }
}
