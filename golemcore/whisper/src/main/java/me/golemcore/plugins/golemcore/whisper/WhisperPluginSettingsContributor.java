package me.golemcore.plugins.golemcore.whisper;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.model.RuntimeConfig;
import me.golemcore.plugin.api.runtime.model.Secret;
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
public class WhisperPluginSettingsContributor implements PluginSettingsContributor {

    private static final String PLUGIN_ID = "golemcore/whisper";
    private static final String SECTION_KEY = "main";

    private final RuntimeConfigService runtimeConfigService;

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PLUGIN_ID)
                .pluginName("whisper")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Voice: Whisper")
                .description("Whisper-compatible STT endpoint URL and optional API key.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(81)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        RuntimeConfig.VoiceConfig voice = runtimeConfigService.getRuntimeConfigForApi().getVoice();
        boolean active = PLUGIN_ID.equals(runtimeConfigService.getSttProvider());
        return PluginSettingsSection.builder()
                .title("Whisper")
                .description("Configure any OpenAI-compatible speech-to-text endpoint as a loadable plugin.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("whisperSttUrl")
                                .type("url")
                                .label("Whisper STT URL")
                                .description("Base URL for the Whisper-compatible server.")
                                .placeholder("http://localhost:5092")
                                .build(),
                        PluginSettingsField.builder()
                                .key("whisperSttApiKey")
                                .type("secret")
                                .label("Whisper API Key")
                                .description("Optional bearer token for the STT endpoint.")
                                .placeholder("Enter API key")
                                .build()))
                .values(buildValues(voice))
                .blocks(List.of(PluginSettingsBlock.builder()
                        .type("notice")
                        .key("provider-state")
                        .title("Provider state")
                        .variant(active ? "info" : "secondary")
                        .text(active
                                ? "Whisper is currently the active STT provider."
                                : "Whisper is configured but inactive. Use Voice Routing to make it the active STT provider.")
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        RuntimeConfig config = runtimeConfigService.getRuntimeConfig();
        RuntimeConfig.VoiceConfig voice = config.getVoice();

        voice.setWhisperSttUrl(blankToNull(readString(values, "whisperSttUrl")));
        String apiKey = readString(values, "whisperSttApiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            voice.setWhisperSttApiKey(Secret.of(apiKey));
        }

        runtimeConfigService.updateRuntimeConfig(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Whisper plugin action: " + actionId);
    }

    private Map<String, Object> buildValues(RuntimeConfig.VoiceConfig voice) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("whisperSttUrl", voice.getWhisperSttUrl() != null ? voice.getWhisperSttUrl() : "");
        values.put("whisperSttApiKey", "");
        return values;
    }

    private String readString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Whisper settings section: " + sectionKey);
        }
    }
}
