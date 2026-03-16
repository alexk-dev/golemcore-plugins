package me.golemcore.plugins.golemcore.elevenlabs;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ElevenLabsPluginConfigService {

    static final String PLUGIN_ID = "golemcore/elevenlabs";

    private final PluginConfigurationService pluginConfigurationService;
    private final RuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;

    public ElevenLabsPluginConfigService(
            PluginConfigurationService pluginConfigurationService,
            RuntimeConfigService runtimeConfigService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = new ObjectMapper();
    }

    public ElevenLabsPluginConfig getConfig() {
        if (!pluginConfigurationService.hasPluginConfig(PLUGIN_ID)) {
            return buildLegacyRuntimeFallback();
        }
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        ElevenLabsPluginConfig config = raw.isEmpty()
                ? ElevenLabsPluginConfig.builder().build()
                : objectMapper.convertValue(raw, ElevenLabsPluginConfig.class);
        if (config == null) {
            config = ElevenLabsPluginConfig.builder().build();
        }
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(ElevenLabsPluginConfig config) {
        ElevenLabsPluginConfig normalized = config != null
                ? config
                : ElevenLabsPluginConfig.builder().build();
        normalized.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(normalized, Map.class));
    }

    private ElevenLabsPluginConfig buildLegacyRuntimeFallback() {
        ElevenLabsPluginConfig config = ElevenLabsPluginConfig.builder()
                .apiKey(blankToNull(runtimeConfigService.getVoiceApiKey()))
                .voiceId(runtimeConfigService.getVoiceId())
                .ttsModelId(runtimeConfigService.getTtsModelId())
                .sttModelId(runtimeConfigService.getSttModelId())
                .speed(runtimeConfigService.getVoiceSpeed())
                .build();
        config.normalize();
        return config;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
