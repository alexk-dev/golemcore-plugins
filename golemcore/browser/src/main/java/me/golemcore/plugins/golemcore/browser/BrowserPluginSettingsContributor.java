package me.golemcore.plugins.golemcore.browser;

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
public class BrowserPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final BrowserPluginConfigService configService;

    public BrowserPluginSettingsContributor(BrowserPluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getPluginId() {
        return BrowserPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(BrowserPluginConfigService.PLUGIN_ID)
                .pluginName("browser")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Browser")
                .description("Playwright-backed web browsing, extraction, and screenshot capture.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(30)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        BrowserPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("headless", Boolean.TRUE.equals(config.getHeadless()));
        values.put("timeoutMs", config.getTimeoutMs());
        values.put("userAgent", config.getUserAgent());
        return PluginSettingsSection.builder()
                .title("Browser")
                .description("Configure the isolated browser automation plugin used by the browse tool.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Browser Tool")
                                .description("Allow web browsing and screenshot capture through the plugin runtime.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("headless")
                                .type("boolean")
                                .label("Headless Browser")
                                .description("Run Playwright without a visible UI.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutMs")
                                .type("number")
                                .label("Timeout (ms)")
                                .description("Maximum navigation timeout for page loads.")
                                .min(1000.0)
                                .max(120000.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("userAgent")
                                .type("text")
                                .label("User-Agent")
                                .description("User-Agent string used for Playwright browser contexts.")
                                .placeholder("Mozilla/5.0 ...")
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        BrowserPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", true));
        config.setHeadless(readBoolean(values, "headless", true));
        config.setTimeoutMs(readInteger(values, "timeoutMs", 30000));
        config.setUserAgent(readString(values, "userAgent", config.getUserAgent()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown browser plugin action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown browser settings section: " + sectionKey);
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
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return defaultValue;
    }
}
