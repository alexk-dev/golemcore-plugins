package me.golemcore.plugins.golemcore.airtable;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiClient;
import me.golemcore.plugins.golemcore.airtable.support.AirtableApiException;
import me.golemcore.plugins.golemcore.airtable.support.AirtableTransportException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AirtablePluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_TEST_CONNECTION = "test-connection";

    private final AirtablePluginConfigService configService;
    private final AirtableApiClient apiClient;

    public AirtablePluginSettingsContributor(
            AirtablePluginConfigService configService,
            AirtableApiClient apiClient) {
        this.configService = configService;
        this.apiClient = apiClient;
    }

    @Override
    public String getPluginId() {
        return AirtablePluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(AirtablePluginConfigService.PLUGIN_ID)
                .pluginName("airtable")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Airtable")
                .description("Airtable base, default table, and write permissions for records operations.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(30)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        AirtablePluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("apiBaseUrl", config.getApiBaseUrl());
        values.put("apiToken", "");
        values.put("baseId", orEmpty(config.getBaseId()));
        values.put("defaultTable", config.getDefaultTable());
        values.put("defaultView", config.getDefaultView());
        values.put("defaultMaxRecords", config.getDefaultMaxRecords());
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        values.put("typecast", Boolean.TRUE.equals(config.getTypecast()));
        return PluginSettingsSection.builder()
                .title("Airtable")
                .description("Configure Airtable API access and the default table used by the airtable_records tool.")
                .fields(List.of(
                        booleanField("enabled", "Enable Airtable",
                                "Allow the model to list and mutate Airtable records."),
                        textField("apiBaseUrl", "API Base URL",
                                "Airtable REST API base URL.", AirtablePluginConfig.DEFAULT_API_BASE_URL),
                        secretField("apiToken", "API Token",
                                "Personal access token. Leave blank to keep the current secret."),
                        textField("baseId", "Base ID",
                                "Airtable base identifier used for requests.", "appXXXXXXXXXXXXXX"),
                        textField("defaultTable", "Default Table",
                                "Optional default table name or ID used when the tool call omits table.",
                                "Tasks"),
                        textField("defaultView", "Default View",
                                "Optional default Airtable view used for list operations.", "Grid view"),
                        numberField("defaultMaxRecords", "Default Max Records",
                                "Maximum records returned when max_records is omitted.", 1.0, 100.0, 1.0),
                        booleanField("allowWrite", "Allow Write",
                                "Permit create_record and update_record operations."),
                        booleanField("allowDelete", "Allow Delete",
                                "Permit delete_record operations."),
                        booleanField("typecast", "Enable Typecast",
                                "Use Airtable typecast for create and update operations by default.")))
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
        AirtablePluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setApiBaseUrl(readString(values, "apiBaseUrl", config.getApiBaseUrl()));
        String apiToken = readString(values, "apiToken", null);
        if (apiToken != null && !apiToken.isBlank()) {
            config.setApiToken(apiToken);
        }
        config.setBaseId(readString(values, "baseId", config.getBaseId()));
        config.setDefaultTable(readString(values, "defaultTable", config.getDefaultTable()));
        config.setDefaultView(readString(values, "defaultView", config.getDefaultView()));
        config.setDefaultMaxRecords(readInteger(values, "defaultMaxRecords", config.getDefaultMaxRecords()));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        config.setTypecast(readBoolean(values, "typecast", false));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (ACTION_TEST_CONNECTION.equals(actionId)) {
            return testConnection();
        }
        throw new IllegalArgumentException("Unknown Airtable action: " + actionId);
    }

    private PluginActionResult testConnection() {
        AirtablePluginConfig config = configService.getConfig();
        if (!hasText(config.getApiToken())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Airtable API token is not configured.")
                    .build();
        }
        if (!hasText(config.getBaseId())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Airtable base ID is not configured.")
                    .build();
        }
        if (!hasText(config.getDefaultTable())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Default Airtable table is not configured.")
                    .build();
        }
        try {
            AirtableApiClient.AirtableListResponse response = apiClient.listRecords(
                    config.getDefaultTable(),
                    config.getDefaultView(),
                    null,
                    1,
                    List.of(),
                    null,
                    null);
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Connected to Airtable. Read access to table "
                            + config.getDefaultTable() + " is available ("
                            + response.records().size() + " record(s) checked).")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | AirtableApiException
                | AirtableTransportException ex) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Connection failed: " + ex.getMessage())
                    .build();
        }
    }

    private PluginSettingsField booleanField(String key, String label, String description) {
        return PluginSettingsField.builder()
                .key(key)
                .type("boolean")
                .label(label)
                .description(description)
                .build();
    }

    private PluginSettingsField textField(String key, String label, String description, String placeholder) {
        return PluginSettingsField.builder()
                .key(key)
                .type("text")
                .label(label)
                .description(description)
                .placeholder(placeholder)
                .build();
    }

    private PluginSettingsField secretField(String key, String label, String description) {
        return PluginSettingsField.builder()
                .key(key)
                .type("secret")
                .label(label)
                .description(description)
                .build();
    }

    private PluginSettingsField numberField(String key, String label, String description,
            Double min, Double max, Double step) {
        return PluginSettingsField.builder()
                .key(key)
                .type("number")
                .label(label)
                .description(description)
                .min(min)
                .max(max)
                .step(step)
                .build();
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Airtable settings section: " + sectionKey);
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
                return Integer.parseInt(text.trim());
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

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
