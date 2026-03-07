package me.golemcore.plugins.golemcore.bravesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;

import java.util.Map;

@Service
public class BraveSearchPluginConfigService {

    static final String PLUGIN_ID = "golemcore/brave-search";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public BraveSearchPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public BraveSearchPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        BraveSearchPluginConfig config = raw.isEmpty()
                ? BraveSearchPluginConfig.builder().build()
                : objectMapper.convertValue(raw, BraveSearchPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(BraveSearchPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
