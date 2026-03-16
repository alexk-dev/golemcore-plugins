package me.golemcore.plugins.golemcore.elevenlabs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElevenLabsPluginConfig {

    public static final String DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM";
    public static final String DEFAULT_TTS_MODEL_ID = "eleven_multilingual_v2";
    public static final String DEFAULT_STT_MODEL_ID = "scribe_v1";
    public static final float DEFAULT_SPEED = 1.0f;

    private String apiKey;
    private String voiceId;
    private String ttsModelId;
    private String sttModelId;
    private Float speed;

    public void normalize() {
        apiKey = blankToNull(apiKey);
        voiceId = defaultIfBlank(voiceId, DEFAULT_VOICE_ID);
        ttsModelId = defaultIfBlank(ttsModelId, DEFAULT_TTS_MODEL_ID);
        sttModelId = defaultIfBlank(sttModelId, DEFAULT_STT_MODEL_ID);
        speed = speed != null && speed > 0 ? speed : DEFAULT_SPEED;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
