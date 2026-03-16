package me.golemcore.plugins.golemcore.elevenlabs;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsBlock;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ElevenLabsPluginSettingsContributor implements PluginSettingsContributor {

    private static final String PLUGIN_ID = "golemcore/elevenlabs";
    private static final String SECTION_KEY = "main";

    private final RuntimeConfigService runtimeConfigService;
    private final ElevenLabsPluginConfigService configService;

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PLUGIN_ID)
                .pluginName("elevenlabs")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Voice: ElevenLabs")
                .description("ElevenLabs credentials, voice ID, and STT/TTS model settings.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(80)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        ElevenLabsPluginConfig config = configService.getConfig();
        boolean sttActive = PLUGIN_ID.equals(runtimeConfigService.getSttProvider());
        boolean ttsActive = PLUGIN_ID.equals(runtimeConfigService.getTtsProvider());

        return PluginSettingsSection.builder()
                .title("ElevenLabs")
                .description("Configure ElevenLabs as an independently loadable voice provider plugin.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("ElevenLabs API Key")
                                .description("Leave blank to keep the current secret.")
                                .placeholder("Enter API key")
                                .build(),
                        PluginSettingsField.builder()
                                .key("voiceId")
                                .type("text")
                                .label("Voice ID")
                                .description("Voice identifier used for ElevenLabs TTS.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("ttsModelId")
                                .type("text")
                                .label("TTS Model")
                                .description("Model id used for ElevenLabs text-to-speech.")
                                .placeholder("eleven_multilingual_v2")
                                .build(),
                        PluginSettingsField.builder()
                                .key("sttModelId")
                                .type("text")
                                .label("STT Model")
                                .description("Model id used for ElevenLabs speech-to-text.")
                                .placeholder("scribe_v1")
                                .build(),
                        PluginSettingsField.builder()
                                .key("speed")
                                .type("number")
                                .label("Voice Speed")
                                .description("Playback speed multiplier for TTS responses.")
                                .min(0.5)
                                .max(2.0)
                                .step(0.1)
                                .build()))
                .values(buildValues(config))
                .blocks(List.of(PluginSettingsBlock.builder()
                        .type("notice")
                        .key("provider-state")
                        .title("Provider state")
                        .variant(sttActive || ttsActive ? "info" : "secondary")
                        .text(String.format(
                                "STT: %s. TTS: %s. Use Voice Routing to switch the active provider selection.",
                                sttActive ? "active" : "inactive",
                                ttsActive ? "active" : "inactive"))
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        ElevenLabsPluginConfig config = configService.getConfig();

        String apiKey = readString(values, "apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setVoiceId(readString(values, "voiceId"));
        config.setTtsModelId(readString(values, "ttsModelId"));
        config.setSttModelId(readString(values, "sttModelId"));
        config.setSpeed(readFloat(values, "speed", config.getSpeed()));

        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown ElevenLabs plugin action: " + actionId);
    }

    private Map<String, Object> buildValues(ElevenLabsPluginConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("apiKey", "");
        values.put("voiceId", config.getVoiceId());
        values.put("ttsModelId", config.getTtsModelId());
        values.put("sttModelId", config.getSttModelId());
        values.put("speed", config.getSpeed());
        return values;
    }

    private Float readFloat(Map<String, Object> values, String key, float fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Float.parseFloat(stringValue);
        }
        return fallback;
    }

    private String readString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown ElevenLabs settings section: " + sectionKey);
        }
    }
}
