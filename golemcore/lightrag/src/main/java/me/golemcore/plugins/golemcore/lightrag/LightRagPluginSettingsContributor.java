package me.golemcore.plugins.golemcore.lightrag;

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
public class LightRagPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final LightRagPluginConfigService configService;

    public LightRagPluginSettingsContributor(LightRagPluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getPluginId() {
        return LightRagPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(LightRagPluginConfigService.PLUGIN_ID)
                .pluginName("lightrag")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("RAG: LightRAG")
                .description("Configure the LightRAG endpoint used for retrieval and indexing.")
                .blockKey("runtime")
                .blockTitle("Runtime")
                .blockDescription("Agent execution, memory, usage, and autonomy")
                .order(60)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        LightRagPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("url", config.getUrl());
        values.put("apiKey", "");
        values.put("queryMode", config.getQueryMode());
        values.put("timeoutSeconds", config.getTimeoutSeconds());
        values.put("indexMinLength", config.getIndexMinLength());
        return PluginSettingsSection.builder()
                .title("LightRAG")
                .description("Configure the isolated LightRAG provider plugin.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable LightRAG")
                                .description("Allow prompt augmentation and async indexing through LightRAG.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("url")
                                .type("url")
                                .label("Base URL")
                                .description("Base URL of the LightRAG REST API.")
                                .placeholder("http://localhost:9621")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("API Key")
                                .description("Optional bearer token for the LightRAG server.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("queryMode")
                                .type("select")
                                .label("Query Mode")
                                .description("Default LightRAG query mode.")
                                .options(List.of("naive", "local", "global", "hybrid").stream()
                                        .map(value -> PluginSettingsFieldOption.builder()
                                                .value(value)
                                                .label(value)
                                                .build())
                                        .toList())
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutSeconds")
                                .type("number")
                                .label("Timeout (seconds)")
                                .description("HTTP timeout for query and indexing requests.")
                                .min(1.0)
                                .max(120.0)
                                .step(1.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("indexMinLength")
                                .type("number")
                                .label("Index Min Length")
                                .description("Skip indexing short or trivial exchanges below this combined length.")
                                .min(1.0)
                                .max(5000.0)
                                .step(1.0)
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        LightRagPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setUrl(readString(values, "url", config.getUrl()));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setQueryMode(readString(values, "queryMode", config.getQueryMode()));
        config.setTimeoutSeconds(readInteger(values, "timeoutSeconds", config.getTimeoutSeconds()));
        config.setIndexMinLength(readInteger(values, "indexMinLength", config.getIndexMinLength()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown LightRAG action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown LightRAG settings section: " + sectionKey);
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
            String trimmed = text.trim();
            return trimmed.isEmpty() ? defaultValue : trimmed;
        }
        return defaultValue;
    }
}
