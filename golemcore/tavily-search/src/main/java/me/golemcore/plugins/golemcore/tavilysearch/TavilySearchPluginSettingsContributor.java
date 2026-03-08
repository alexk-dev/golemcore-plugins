package me.golemcore.plugins.golemcore.tavilysearch;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsFieldOption;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TavilySearchPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final TavilySearchPluginConfigService configService;

    @Override
    public String getPluginId() {
        return TavilySearchPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(TavilySearchPluginConfigService.PLUGIN_ID)
                .pluginName("tavily-search")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Tavily Search")
                .description("Tavily API credentials and search defaults.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(34)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        TavilySearchPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiKey", "");
        values.put("defaultMaxResults", config.getDefaultMaxResults());
        values.put("defaultTopic", config.getDefaultTopic());
        values.put("defaultSearchDepth", config.getDefaultSearchDepth());
        values.put("includeAnswer", Boolean.TRUE.equals(config.getIncludeAnswer()));
        values.put("includeRawContent", Boolean.TRUE.equals(config.getIncludeRawContent()));
        return PluginSettingsSection.builder()
                .title("Tavily Search")
                .description("Configure Tavily web search defaults and API credentials.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Tavily Search")
                                .description("Allow the model to execute Tavily-backed web searches.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("Tavily API Key")
                                .description("Bearer token from your Tavily account.")
                                .placeholder("tvly-...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultMaxResults")
                                .type("number")
                                .label("Default Result Count")
                                .description("Number of search results returned when max_results is omitted.")
                                .min(1.0)
                                .max(20.0)
                                .step(1.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultTopic")
                                .type("select")
                                .label("Default Topic")
                                .description("Search corpus used when the tool call does not specify topic.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("general").label("General").build(),
                                        PluginSettingsFieldOption.builder().value("news").label("News").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultSearchDepth")
                                .type("select")
                                .label("Default Search Depth")
                                .description("Use advanced depth for broader retrieval when needed.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("basic").label("Basic").build(),
                                        PluginSettingsFieldOption.builder().value("advanced").label("Advanced")
                                                .build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("includeAnswer")
                                .type("boolean")
                                .label("Include Tavily Answer")
                                .description("Request Tavily's synthesized answer in addition to raw results.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("includeRawContent")
                                .type("boolean")
                                .label("Include Raw Content")
                                .description("Request raw page content for richer search context.")
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        TavilySearchPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setDefaultMaxResults(readInteger(values, "defaultMaxResults", config.getDefaultMaxResults()));
        config.setDefaultTopic(readString(values, "defaultTopic", config.getDefaultTopic()));
        config.setDefaultSearchDepth(readString(values, "defaultSearchDepth", config.getDefaultSearchDepth()));
        config.setIncludeAnswer(readBoolean(values, "includeAnswer", Boolean.TRUE.equals(config.getIncludeAnswer())));
        config.setIncludeRawContent(
                readBoolean(values, "includeRawContent", Boolean.TRUE.equals(config.getIncludeRawContent())));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Tavily Search action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Tavily Search settings section: " + sectionKey);
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
