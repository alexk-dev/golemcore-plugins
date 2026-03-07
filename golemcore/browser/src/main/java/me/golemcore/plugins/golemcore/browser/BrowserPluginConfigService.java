package me.golemcore.plugins.golemcore.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;

import java.util.Map;

@Service
public class BrowserPluginConfigService {

    static final String PLUGIN_ID = "golemcore/browser";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public BrowserPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public BrowserPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        BrowserPluginConfig config = raw.isEmpty()
                ? BrowserPluginConfig.builder().build()
                : objectMapper.convertValue(raw, BrowserPluginConfig.class);
        config.normalize();
        return config;
    }

    public void save(BrowserPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
