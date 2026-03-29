package me.golemcore.plugins.golemcore.obsidian;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ObsidianPluginConfigService {

    static final String PLUGIN_ID = "golemcore/obsidian";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObsidianPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        ObsidianPluginConfig config = raw.isEmpty()
                ? ObsidianPluginConfig.builder().build()
                : objectMapper.convertValue(raw, ObsidianPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(ObsidianPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
