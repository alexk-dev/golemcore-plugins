package me.golemcore.plugins.golemcore.obsidian;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ObsidianPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final ObsidianPluginConfigService configService;

    @Override
    public String getPluginId() {
        return ObsidianPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(ObsidianPluginConfigService.PLUGIN_ID)
                .pluginName("obsidian")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Obsidian")
                .description("Obsidian vault connection, context limits, and file operation policy.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(37)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        ObsidianPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("baseUrl", config.getBaseUrl());
        values.put("apiKey", "");
        values.put("timeoutMs", config.getTimeoutMs());
        values.put("allowInsecureTls", Boolean.TRUE.equals(config.getAllowInsecureTls()));
        values.put("defaultSearchContextLength", config.getDefaultSearchContextLength());
        values.put("maxReadChars", config.getMaxReadChars());
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        values.put("allowMove", Boolean.TRUE.equals(config.getAllowMove()));
        values.put("allowRename", Boolean.TRUE.equals(config.getAllowRename()));

        return PluginSettingsSection.builder()
                .title("Obsidian")
                .description("Configure the Obsidian vault connection and conservative file operation policy.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Obsidian")
                                .description("Allow tools to use the Obsidian vault integration.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("baseUrl")
                                .type("text")
                                .label("Base URL")
                                .description("Obsidian Local REST API endpoint.")
                                .placeholder("http://127.0.0.1:27123")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("API Key")
                                .description("Leave blank to keep the current secret.")
                                .placeholder("Enter API key")
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutMs")
                                .type("number")
                                .label("Request Timeout (ms)")
                                .description("Timeout for vault API requests.")
                                .min(1000.0)
                                .max(300000.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowInsecureTls")
                                .type("boolean")
                                .label("Allow Insecure TLS")
                                .description("Allow self-signed TLS certificates when connecting to Obsidian.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultSearchContextLength")
                                .type("number")
                                .label("Default Search Context Length")
                                .description("Context length used when searching vault content.")
                                .min(1.0)
                                .step(1.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("maxReadChars")
                                .type("number")
                                .label("Max Read Chars")
                                .description("Maximum number of characters returned when reading a note.")
                                .min(1.0)
                                .step(1.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowWrite")
                                .type("boolean")
                                .label("Allow Write")
                                .description("Permit tools to create or edit notes.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowDelete")
                                .type("boolean")
                                .label("Allow Delete")
                                .description("Permit tools to delete notes.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowMove")
                                .type("boolean")
                                .label("Allow Move")
                                .description("Permit tools to move notes or files.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowRename")
                                .type("boolean")
                                .label("Allow Rename")
                                .description("Permit tools to rename notes or files.")
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        ObsidianPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setBaseUrl(readString(values, "baseUrl", config.getBaseUrl()));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        config.setAllowInsecureTls(readBoolean(values, "allowInsecureTls", false));
        config.setDefaultSearchContextLength(readInteger(values, "defaultSearchContextLength",
                config.getDefaultSearchContextLength()));
        config.setMaxReadChars(readInteger(values, "maxReadChars", config.getMaxReadChars()));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        config.setAllowMove(readBoolean(values, "allowMove", false));
        config.setAllowRename(readBoolean(values, "allowRename", false));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Obsidian action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Obsidian settings section: " + sectionKey);
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
