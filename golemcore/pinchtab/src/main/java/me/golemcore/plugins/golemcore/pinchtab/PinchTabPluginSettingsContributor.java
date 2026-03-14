package me.golemcore.plugins.golemcore.pinchtab;

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
public class PinchTabPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final PinchTabPluginConfigService configService;

    @Override
    public String getPluginId() {
        return PinchTabPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(PinchTabPluginConfigService.PLUGIN_ID)
                .pluginName("pinchtab")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("PinchTab")
                .description("PinchTab server settings and browser automation defaults.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(37)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        PinchTabPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("baseUrl", config.getBaseUrl());
        values.put("apiToken", "");
        values.put("defaultInstanceId", config.getDefaultInstanceId());
        values.put("requestTimeoutMs", config.getRequestTimeoutMs());
        values.put("defaultWaitFor", config.getDefaultWaitFor());
        values.put("defaultBlockImages", Boolean.TRUE.equals(config.getDefaultBlockImages()));
        values.put("defaultSnapshotFilter", config.getDefaultSnapshotFilter());
        values.put("defaultSnapshotFormat", config.getDefaultSnapshotFormat());
        values.put("defaultTextMode", config.getDefaultTextMode());
        values.put("defaultScreenshotQuality", config.getDefaultScreenshotQuality());
        return PluginSettingsSection.builder()
                .title("PinchTab")
                .description("Configure the PinchTab HTTP endpoint and default browsing behavior.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable PinchTab")
                                .description("Allow the model to control a PinchTab browser service.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("baseUrl")
                                .type("text")
                                .label("Base URL")
                                .description("PinchTab server or bridge base URL.")
                                .placeholder("http://127.0.0.1:9867")
                                .build(),
                        PluginSettingsField.builder()
                                .key("apiToken")
                                .type("secret")
                                .label("API Token")
                                .description("Optional Bearer token when PinchTab is protected.")
                                .placeholder("pt_...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultInstanceId")
                                .type("text")
                                .label("Default Instance ID")
                                .description("Optional orchestrator instance to use when tools omit instance_id.")
                                .placeholder("inst_...")
                                .build(),
                        PluginSettingsField.builder()
                                .key("requestTimeoutMs")
                                .type("number")
                                .label("Request Timeout (ms)")
                                .description("HTTP timeout for PinchTab requests.")
                                .min(1000.0)
                                .max(300000.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultWaitFor")
                                .type("select")
                                .label("Default Navigate Wait")
                                .description("Default wait mode for top-level navigation requests.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("none").label("none").build(),
                                        PluginSettingsFieldOption.builder().value("dom").label("dom").build(),
                                        PluginSettingsFieldOption.builder()
                                                .value("networkidle")
                                                .label("networkidle")
                                                .build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultBlockImages")
                                .type("boolean")
                                .label("Block Images By Default")
                                .description("Reduce page weight for read-heavy automation flows.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultSnapshotFilter")
                                .type("select")
                                .label("Default Snapshot Filter")
                                .description("Interactive mode is usually the most token-efficient default.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder()
                                                .value("interactive")
                                                .label("interactive")
                                                .build(),
                                        PluginSettingsFieldOption.builder().value("all").label("all").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultSnapshotFormat")
                                .type("select")
                                .label("Default Snapshot Format")
                                .description("Compact is the recommended default for LLM browsing loops.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder().value("compact").label("compact").build(),
                                        PluginSettingsFieldOption.builder().value("text").label("text").build(),
                                        PluginSettingsFieldOption.builder().value("json").label("json").build(),
                                        PluginSettingsFieldOption.builder().value("yaml").label("yaml").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultTextMode")
                                .type("select")
                                .label("Default Text Mode")
                                .description("Readability returns cleaner article text than raw extraction.")
                                .options(List.of(
                                        PluginSettingsFieldOption.builder()
                                                .value("readability")
                                                .label("readability")
                                                .build(),
                                        PluginSettingsFieldOption.builder().value("raw").label("raw").build()))
                                .build(),
                        PluginSettingsField.builder()
                                .key("defaultScreenshotQuality")
                                .type("number")
                                .label("Screenshot Quality")
                                .description("JPEG quality used when screenshot tool omits quality.")
                                .min(1.0)
                                .max(100.0)
                                .step(1.0)
                                .build()))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        PinchTabPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setBaseUrl(readString(values, "baseUrl", config.getBaseUrl()));
        String apiToken = readString(values, "apiToken", null);
        if (apiToken != null && !apiToken.isBlank()) {
            config.setApiToken(apiToken);
        }
        config.setDefaultInstanceId(readString(values, "defaultInstanceId", config.getDefaultInstanceId()));
        config.setRequestTimeoutMs(readInteger(values, "requestTimeoutMs", config.getRequestTimeoutMs()));
        config.setDefaultWaitFor(readString(values, "defaultWaitFor", config.getDefaultWaitFor()));
        config.setDefaultBlockImages(readBoolean(values, "defaultBlockImages", config.getDefaultBlockImages()));
        config.setDefaultSnapshotFilter(readString(values, "defaultSnapshotFilter", config.getDefaultSnapshotFilter()));
        config.setDefaultSnapshotFormat(readString(values, "defaultSnapshotFormat", config.getDefaultSnapshotFormat()));
        config.setDefaultTextMode(readString(values, "defaultTextMode", config.getDefaultTextMode()));
        config.setDefaultScreenshotQuality(
                readInteger(values, "defaultScreenshotQuality", config.getDefaultScreenshotQuality()));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown PinchTab action: " + actionId);
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown PinchTab settings section: " + sectionKey);
        }
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
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
