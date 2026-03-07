package me.golemcore.plugins.golemcore.bravesearch;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BraveSearchPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final BraveSearchPluginConfigService configService;

    public BraveSearchPluginSettingsContributor(BraveSearchPluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getPluginId() {
        return BraveSearchPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(BraveSearchPluginConfigService.PLUGIN_ID)
                .pluginName("brave-search")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Brave Search")
                .description("Brave Search API credentials and default result count.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(31)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        BraveSearchPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiKey", "");
        values.put("defaultCount", config.getDefaultCount());
        return PluginSettingsSection.builder()
                .title("Brave Search")
                .description("Configure the Brave Search tool plugin and its API credentials.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Brave Search")
                                .description("Allow the model to execute Brave web searches.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("Brave API Key")
                                .description("Subscription token from brave.com/search/api.")
                                .placeholder("Enter API key")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultCount")
                                .type("number")
                                .label("Default Result Count")
                                .description("Number of results returned when count is not specified.")
                                .min(1.0)
                                .max(20.0)
                                .step(1.0)
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        BraveSearchPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setDefaultCount(readInteger(values, "defaultCount", config.getDefaultCount()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Brave Search action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Brave Search settings section: " + sectionKey);
        }
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private int readInteger(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String readString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        return defaultValue;
    }
}
