package me.golemcore.plugins.golemcore.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.runtime.PluginConfigurationService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SupabasePluginConfigService {

    static final String PLUGIN_ID = "golemcore/supabase";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public SupabasePluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public SupabasePluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        SupabasePluginConfig config = raw.isEmpty()
                ? SupabasePluginConfig.builder().build()
                : objectMapper.convertValue(raw, SupabasePluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(SupabasePluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
