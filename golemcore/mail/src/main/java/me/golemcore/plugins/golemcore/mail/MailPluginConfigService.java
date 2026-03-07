package me.golemcore.plugins.golemcore.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import me.golemcore.plugin.api.runtime.PluginConfigurationService;

import java.util.Map;

@Service
public class MailPluginConfigService {

    static final String PLUGIN_ID = "golemcore/mail";

    private final PluginConfigurationService pluginConfigurationService;
    private final ObjectMapper objectMapper;

    public MailPluginConfigService(PluginConfigurationService pluginConfigurationService) {
        this.pluginConfigurationService = pluginConfigurationService;
        this.objectMapper = new ObjectMapper();
    }

    public MailPluginConfig getConfig() {
        Map<String, Object> raw = pluginConfigurationService.getPluginConfig(PLUGIN_ID);
        MailPluginConfig config = raw.isEmpty()
                ? MailPluginConfig.builder().build()
                : objectMapper.convertValue(raw, MailPluginConfig.class);
        config.normalize();
        return config;
    }

    @SuppressWarnings("unchecked")
    public void save(MailPluginConfig config) {
        config.normalize();
        pluginConfigurationService.savePluginConfig(PLUGIN_ID, objectMapper.convertValue(config, Map.class));
    }
}
