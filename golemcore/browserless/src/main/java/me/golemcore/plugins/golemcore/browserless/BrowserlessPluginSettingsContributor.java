package me.golemcore.plugins.golemcore.browserless;

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
public class BrowserlessPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final BrowserlessPluginConfigService configService;

    @Override
    public String getPluginId() {
        return BrowserlessPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(BrowserlessPluginConfigService.PLUGIN_ID)
                .pluginName("browserless")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Browserless")
                .description("Browserless API credentials and smart scrape defaults.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(36)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        BrowserlessPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiKey", "");
        values.put("baseUrl", config.getBaseUrl());
        values.put("defaultFormat", config.getDefaultFormat());
        values.put("bestAttempt", Boolean.TRUE.equals(config.getBestAttempt()));
        values.put("gotoWaitUntil", config.getGotoWaitUntil());
        values.put("gotoTimeoutMs", config.getGotoTimeoutMs());
        values.put("timeoutMs", config.getTimeoutMs());
        return PluginSettingsSection.builder()
                .title("Browserless")
                .description("Configure Browserless smart scrape behavior and API credentials.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Browserless")
                                .description("Allow the model to scrape rendered pages through Browserless.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("Browserless API Key")
                                .description("API token from your Browserless account.")
                                .placeholder("bl_...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("baseUrl")
                                .type("text")
                                .label("Base URL")
                                .description(
                                        "Browserless REST API base URL, useful for regional or self-hosted deployments.")
                                .placeholder("https://production-sfo.browserless.io")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultFormat")
                                .type("select")
                                .label("Default Format")
                                .description("Primary content format returned by browserless_smart_scrape.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("markdown").label("Markdown").build(),
                                        PluginSettingsFieldOption.builder().value("html").label("HTML").build(),
                                        PluginSettingsFieldOption.builder().value("links").label("Links").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("bestAttempt")
                                .type("boolean")
                                .label("Best Attempt")
                                .description(
                                        "Continue when wait or navigation hooks time out and return partial output.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("gotoWaitUntil")
                                .type("select")
                                .label("Default waitUntil")
                                .description("Navigation readiness mode for page loads.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("load").label("load").build(),
                                        PluginSettingsFieldOption.builder()
                                                .value("domcontentloaded")
                                                .label("domcontentloaded")
                                                .build(),
                                        PluginSettingsFieldOption.builder()
                                                .value("networkidle0")
                                                .label("networkidle0")
                                                .build(),
                                        PluginSettingsFieldOption.builder()
                                                .value("networkidle2")
                                                .label("networkidle2")
                                                .build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("gotoTimeoutMs")
                                .type("number")
                                .label("Navigation Timeout (ms)")
                                .description("Default timeout for the underlying page navigation.")
                                .min(1000.0)
                                .max(300000.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutMs")
                                .type("number")
                                .label("Request Timeout (ms)")
                                .description("Global Browserless request timeout.")
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
        BrowserlessPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setBaseUrl(readString(values, "baseUrl", config.getBaseUrl()));
        config.setDefaultFormat(readString(values, "defaultFormat", config.getDefaultFormat()));
        config.setBestAttempt(readBoolean(values, "bestAttempt", config.getBestAttempt()));
        config.setGotoWaitUntil(readString(values, "gotoWaitUntil", config.getGotoWaitUntil()));
        config.setGotoTimeoutMs(readInteger(values, "gotoTimeoutMs", config.getGotoTimeoutMs()));
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Browserless action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Browserless settings section: " + sectionKey);
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
