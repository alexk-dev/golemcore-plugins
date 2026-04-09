package me.golemcore.plugins.golemcore.airtable;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AirtablePluginConfigService {

    static final String PLUGIN_ID = "golemcore/airtable";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public AirtablePluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public AirtablePluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        AirtablePluginConfig config = raw.isEmpty()
                ? AirtablePluginConfig.builder().build()
                : objectMapper.convertValue(raw, AirtablePluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(AirtablePluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
