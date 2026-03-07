package me.golemcore.plugins.golemcore.lightrag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;

import java.util.Map;

@Service
public class LightRagPluginConfigService {

    static final String PLUGIN_ID = "golemcore/lightrag";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public LightRagPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public LightRagPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        LightRagPluginConfig config = raw.isEmpty()
                ? LightRagPluginConfig.builder().build()
                : objectMapper.convertValue(raw, LightRagPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(LightRagPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
