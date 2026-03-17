package me.golemcore.plugins.golemcore.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SlackPluginConfigService {

    static final String PLUGIN_ID = "golemcore/slack";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        SlackPluginConfig config = raw.isEmpty()
                ? SlackPluginConfig.builder().build()
                : objectMapper.convertValue(raw, SlackPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(SlackPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
