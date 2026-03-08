package me.golemcore.plugins.golemcore.firecrawl;

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
public class FirecrawlPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final FirecrawlPluginConfigService configService;

    @Override
    public String getPluginId() {
        return FirecrawlPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(FirecrawlPluginConfigService.PLUGIN_ID)
                .pluginName("firecrawl")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Firecrawl")
                .description("Firecrawl API credentials and scraping defaults.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(35)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        FirecrawlPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiKey", "");
        values.put("defaultFormat", config.getDefaultFormat());
        values.put("onlyMainContent", Boolean.TRUE.equals(config.getOnlyMainContent()));
        values.put("maxAgeMs", config.getMaxAgeMs());
        values.put("timeoutMs", config.getTimeoutMs());
        return PluginSettingsSection.builder()
                .title("Firecrawl")
                .description("Configure Firecrawl scraping behavior and API credentials.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Firecrawl")
                                .description("Allow the model to scrape pages through Firecrawl.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("Firecrawl API Key")
                                .description("API key from your Firecrawl account.")
                                .placeholder("fc-...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultFormat")
                                .type("select")
                                .label("Default Format")
                                .description("Primary content format returned by firecrawl_scrape.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("markdown").label("Markdown").build(),
                                        PluginSettingsFieldOption.builder().value("summary").label("Summary").build(),
                                        PluginSettingsFieldOption.builder().value("html").label("HTML").build(),
                                        PluginSettingsFieldOption.builder().value("rawHtml").label("Raw HTML").build(),
                                        PluginSettingsFieldOption.builder().value("links").label("Links").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("onlyMainContent")
                                .type("boolean")
                                .label("Only Main Content")
                                .description("Strip navigation, footers, and surrounding chrome when possible.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("maxAgeMs")
                                .type("number")
                                .label("Default Cache Max Age")
                                .description(
                                        "Maximum cache age in milliseconds before Firecrawl fetches fresh content.")
                                .min(0.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutMs")
                                .type("number")
                                .label("Timeout (ms)")
                                .description("Request timeout for Firecrawl scraping operations.")
                                .min(1000.0)
                                .max(300000.0)
                                .step(1000.0)
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        FirecrawlPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setDefaultFormat(readString(values, "defaultFormat", config.getDefaultFormat()));
        config.setOnlyMainContent(readBoolean(values, "onlyMainContent", config.getOnlyMainContent()));
        config.setMaxAgeMs(readInteger(values, "maxAgeMs", config.getMaxAgeMs()));
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Firecrawl action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Firecrawl settings section: " + sectionKey);
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
