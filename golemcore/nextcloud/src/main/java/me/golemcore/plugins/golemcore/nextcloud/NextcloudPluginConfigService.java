package me.golemcore.plugins.golemcore.nextcloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NextcloudPluginConfigService {

    static final String PLUGIN_ID = "golemcore/nextcloud";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NextcloudPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        NextcloudPluginConfig config = raw.isEmpty()
                ? NextcloudPluginConfig.builder().build()
                : objectMapper.convertValue(raw, NextcloudPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(NextcloudPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
