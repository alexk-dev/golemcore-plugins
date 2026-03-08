package me.golemcore.plugins.golemcore.browserless;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrowserlessPluginConfigService {

    static final String PLUGIN_ID = "golemcore/browserless";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BrowserlessPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        BrowserlessPluginConfig config = raw.isEmpty()
                ? BrowserlessPluginConfig.builder().build()
                : objectMapper.convertValue(raw, BrowserlessPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(BrowserlessPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
