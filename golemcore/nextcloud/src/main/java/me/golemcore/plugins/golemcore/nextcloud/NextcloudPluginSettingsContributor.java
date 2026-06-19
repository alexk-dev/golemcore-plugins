package me.golemcore.plugins.golemcore.nextcloud;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudApiException;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudTransportException;
import me.golemcore.plugins.golemcore.nextcloud.support.NextcloudWebDavClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NextcloudPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_TEST_CONNECTION = "test-connection";

    private final NextcloudPluginConfigService configService;
    private final NextcloudWebDavClient client;

    @Override
    public String getPluginId() {
        return NextcloudPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(NextcloudPluginConfigService.PLUGIN_ID)
                .pluginName("nextcloud")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Nextcloud")
                .description("Nextcloud WebDAV connection, root sandbox, and file operation policy.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(39)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        NextcloudPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("baseUrl", config.getBaseUrl());
        values.put("username", config.getUsername());
        values.put("appPassword", "");
        values.put("rootPath", config.getRootPath().isBlank() ? "/" : "/" + config.getRootPath());
        values.put("timeoutMs", config.getTimeoutMs());
        values.put("allowInsecureTls", Boolean.TRUE.equals(config.getAllowInsecureTls()));
        values.put("maxDownloadBytes", config.getMaxDownloadBytes());
        values.put("maxInlineTextChars", config.getMaxInlineTextChars());
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        values.put("allowMove", Boolean.TRUE.equals(config.getAllowMove()));
        values.put("allowCopy", Boolean.TRUE.equals(config.getAllowCopy()));

        return PluginSettingsSection.builder()
                .title("Nextcloud")
                .description("Configure the Nextcloud WebDAV connection, root sandbox, and conservative file policy.")
                .fields(List.of(
                        PluginSettingsField.builder()
                                .key("enabled")
                                .type("boolean")
                                .label("Enable Nextcloud")
                                .description("Allow tools to use the Nextcloud file integration.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("baseUrl")
                                .type("text")
                                .label("Base URL")
                                .description("Nextcloud base URL without the WebDAV suffix.")
                                .placeholder("https://nextcloud.example.com")
                                .build(),
                        PluginSettingsField.builder()
                                .key("username")
                                .type("text")
                                .label("Username")
                                .description("Nextcloud username used for the standard files WebDAV endpoint.")
                                .placeholder("alex")
                                .build(),
                        PluginSettingsField.builder()
                                .key("appPassword")
                                .type("secret")
                                .label("App Password")
                                .description("Leave blank to keep the current secret.")
                                .placeholder("Enter app password")
                                .build(),
                        PluginSettingsField.builder()
                                .key("rootPath")
                                .type("text")
                                .label("Root Path")
                                .description("Sandbox all tool paths under this Nextcloud directory.")
                                .placeholder("/AI")
                                .build(),
                        PluginSettingsField.builder()
                                .key("timeoutMs")
                                .type("number")
                                .label("Request Timeout (ms)")
                                .description("Timeout for WebDAV requests.")
                                .min(1000.0)
                                .max(300000.0)
                                .step(1000.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowInsecureTls")
                                .type("boolean")
                                .label("Allow Insecure TLS")
                                .description("Allow self-signed TLS certificates when connecting to Nextcloud.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("maxDownloadBytes")
                                .type("number")
                                .label("Max Download Bytes")
                                .description("Maximum file size that read_file downloads into memory.")
                                .min(1.0)
                                .step(1024.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("maxInlineTextChars")
                                .type("number")
                                .label("Max Inline Text Chars")
                                .description(
                                        "Maximum number of text characters returned inline before attachment fallback.")
                                .min(1.0)
                                .step(1.0)
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowWrite")
                                .type("boolean")
                                .label("Allow Write")
                                .description("Permit tools to create directories or write files.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowDelete")
                                .type("boolean")
                                .label("Allow Delete")
                                .description("Permit tools to delete files or directories.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowMove")
                                .type("boolean")
                                .label("Allow Move")
                                .description("Permit tools to move or rename files and directories.")
                                .build(),
                        PluginSettingsField.builder()
                                .key("allowCopy")
                                .type("boolean")
                                .label("Allow Copy")
                                .description("Permit tools to copy files and directories.")
                                .build()))
                .values(values)
                .actions(List.of(PluginSettingsAction.builder()
                        .actionId(ACTION_TEST_CONNECTION)
                        .label("Test Connection")
                        .variant("secondary")
                        .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        NextcloudPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setBaseUrl(readString(values, "baseUrl", config.getBaseUrl()));
        config.setUsername(readString(values, "username", config.getUsername()));
        String appPassword = readString(values, "appPassword", null);
        if (appPassword != null && !appPassword.isBlank()) {
            config.setAppPassword(appPassword);
        }
        config.setRootPath(readString(values, "rootPath", config.getRootPath()));
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        config.setAllowInsecureTls(readBoolean(values, "allowInsecureTls", false));
        config.setMaxDownloadBytes(readInteger(values, "maxDownloadBytes", config.getMaxDownloadBytes()));
        config.setMaxInlineTextChars(readInteger(values, "maxInlineTextChars", config.getMaxInlineTextChars()));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        config.setAllowMove(readBoolean(values, "allowMove", false));
        config.setAllowCopy(readBoolean(values, "allowCopy", false));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (!ACTION_TEST_CONNECTION.equals(actionId)) {
            throw new IllegalArgumentException("Unknown Nextcloud action: " + actionId);
        }
        return testConnection();
    }

    private PluginActionResult testConnection() {
        NextcloudPluginConfig config = configService.getConfig();
        if (!hasText(config.getUsername()) || !hasText(config.getAppPassword())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Nextcloud username and app password must be configured.")
                    .build();
        }
        try {
            int count = client.listDirectory("").size();
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Connected to Nextcloud. Root returned " + count + " item(s).")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | NextcloudApiException
                | NextcloudTransportException ex) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Connection failed: " + ex.getMessage())
                    .build();
        }
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Nextcloud settings section: " + sectionKey);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
