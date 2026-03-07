package me.golemcore.plugins.golemcore.weather;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsBlock;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WeatherPluginSettingsContributor implements PluginSettingsContributor {

    private static final String PLUGIN_ID = "golemcore/weather";
    private static final String SECTION_KEY = "main";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PLUGIN_ID)
                .pluginName("weather")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Weather")
                .description("Open-Meteo powered current weather tool.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(32)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        return PluginSettingsSection.builder()
                .title("Weather")
                .description("This plugin uses Open-Meteo and does not require any additional credentials.")
                .fields(List.of())
                .values(Map.of())
                .blocks(List.of(PluginSettingsBlock.builder()
                        .type("notice")
                        .key("weather-info")
                        .title("No setup required")
                        .variant("secondary")
                        .text("Weather lookups are ready as soon as the plugin is loaded.")
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown weather plugin action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown weather settings section: " + sectionKey);
        }
    }
}
