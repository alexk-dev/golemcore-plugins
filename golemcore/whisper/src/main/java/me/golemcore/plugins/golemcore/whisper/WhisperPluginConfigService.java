package me.golemcore.plugins.golemcore.whisper;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WhisperPluginConfigService {

    static final String PLUGIN_ID = "golemcore/whisper";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public WhisperPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public WhisperPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.hasPluginConfig(PLUGIN_ID)
                ? pluginConfigurationService.getPluginConfig(PLUGIN_ID)
                : Map.of();
        WhisperPluginConfig config = raw == null || raw.isEmpty()
                ? WhisperPluginConfig.builder().build()
                : objectMapper.convertValue(raw, WhisperPluginConfig.class);
        if (config == null) {
            config = WhisperPluginConfig.builder().build();
        }
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(WhisperPluginConfig config) {
        WhisperPluginConfig normalized = config != null
                ? config
                : WhisperPluginConfig.builder().build();
        normalized.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(normalized, Map.class));
    }
}
