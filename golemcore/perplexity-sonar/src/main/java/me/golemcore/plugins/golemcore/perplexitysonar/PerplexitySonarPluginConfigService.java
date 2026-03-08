package me.golemcore.plugins.golemcore.perplexitysonar;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerplexitySonarPluginConfigService {

    static final String PLUGIN_ID = "golemcore/perplexity-sonar";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PerplexitySonarPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        PerplexitySonarPluginConfig config = raw.isEmpty()
                ? PerplexitySonarPluginConfig.builder().build()
                : objectMapper.convertValue(raw, PerplexitySonarPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(PerplexitySonarPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
