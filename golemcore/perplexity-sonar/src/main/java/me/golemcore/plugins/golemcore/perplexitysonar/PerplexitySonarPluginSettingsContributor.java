package me.golemcore.plugins.golemcore.perplexitysonar;

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
public class PerplexitySonarPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final PerplexitySonarPluginConfigService configService;

    @Override
    public String getPluginId() {
        return PerplexitySonarPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PerplexitySonarPluginConfigService.PLUGIN_ID)
                .pluginName("perplexity-sonar")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Perplexity Sonar")
                .description("Perplexity API credentials, default model, and synchronous search behavior.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(36)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        PerplexitySonarPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiKey", "");
        values.put("defaultModel", config.getDefaultModel());
        values.put("defaultSearchMode", config.getDefaultSearchMode());
        values.put("returnRelatedQuestions", Boolean.TRUE.equals(config.getReturnRelatedQuestions()));
        values.put("returnImages", Boolean.TRUE.equals(config.getReturnImages()));
        return PluginSettingsSection.builder()
                .title("Perplexity Sonar")
                .description("Configure the Perplexity Sonar model, search mode, and API credentials.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Perplexity Sonar")
                                .description("Allow the model to call Perplexity's grounded-answer API.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("Perplexity API Key")
                                .description("Bearer token from your Perplexity API account.")
                                .placeholder("pplx-...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultModel")
                                .type("select")
                                .label("Default Model")
                                .description("Default synchronous Sonar model used by perplexity_ask.")
                                .options(List.of(
                                        option("sonar", "sonar"),
                                        option("sonar-pro", "sonar-pro"),
                                        option("sonar-deep-research", "sonar-deep-research"),
                                        option("sonar-reasoning-pro", "sonar-reasoning-pro")))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultSearchMode")
                                .type("select")
                                .label("Default Search Mode")
                                .description("Search scope used when the tool call does not override it.")
                                .options(List.of(
                                        option("web", "web"),
                                        option("academic", "academic"),
                                        option("sec", "sec")))
                                .build(),
                        PluginSettingsField.builder()
                                .key("returnRelatedQuestions")
                                .type("boolean")
                                .label("Return Related Questions")
                                .description("Ask Perplexity to return related follow-up questions.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("returnImages")
                                .type("boolean")
                                .label("Return Images")
                                .description("Ask Perplexity to include image URLs in the response payload.")
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        PerplexitySonarPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setDefaultModel(readString(values, "defaultModel", config.getDefaultModel()));
        config.setDefaultSearchMode(readString(values, "defaultSearchMode", config.getDefaultSearchMode()));
        config.setReturnRelatedQuestions(readBoolean(values, "returnRelatedQuestions",
                Boolean.TRUE.equals(config.getReturnRelatedQuestions())));
        config.setReturnImages(readBoolean(values, "returnImages", Boolean.TRUE.equals(config.getReturnImages())));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown Perplexity Sonar action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Perplexity Sonar settings section: " + sectionKey);
        }
    }

    private PluginSettingsFieldOption option(String value, String label) {
        return PluginSettingsFieldOption.builder().value(value).label(label).build();
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String readString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        return defaultValue;
    }
}
