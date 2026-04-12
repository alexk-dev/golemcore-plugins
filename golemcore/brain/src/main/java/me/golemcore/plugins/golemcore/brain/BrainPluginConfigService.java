package me.golemcore.plugins.golemcore.brain;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class BrainPluginConfigService {

    static final String PLUGIN_ID = "golemcore/brain";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    @Autowired
    public BrainPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public BrainPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService == null
                ? Map.of()
                : pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        BrainPluginConfig config = raw.isEmpty()
                ? BrainPluginConfig.builder().build()
                : objectMapper.convertValue(raw, BrainPluginConfig.class);
        config.normalize();
        return config;
    }

    public void save(BrainPluginConfig config) {
        config.normalize();
        Map<String, Object> saved = objectMapper.convertValue(config, new TypeReference<>() {
        });
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, saved);
    }
}
