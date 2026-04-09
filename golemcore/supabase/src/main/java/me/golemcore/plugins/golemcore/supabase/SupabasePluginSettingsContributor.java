package me.golemcore.plugins.golemcore.supabase;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiClient;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseApiException;
import me.golemcore.plugins.golemcore.supabase.support.SupabaseTransportException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SupabasePluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_TEST_CONNECTION = "test-connection";

    private final SupabasePluginConfigService configService;
    private final SupabaseApiClient apiClient;

    public SupabasePluginSettingsContributor(
            SupabasePluginConfigService configService,
            SupabaseApiClient apiClient) {
        this.configService = configService;
        this.apiClient = apiClient;
    }

    @Override
    public String getPluginId() {
        return SupabasePluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(SupabasePluginConfigService.PLUGIN_ID)
                .pluginName("supabase")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Supabase")
                .description("Supabase project, schema, and write permissions for table row operations.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(34)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        SupabasePluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("projectUrl", orEmpty(config.getProjectUrl()));
        values.put("apiKey", "");
        values.put("defaultSchema", config.getDefaultSchema());
        values.put("defaultTable", config.getDefaultTable());
        values.put("defaultSelect", config.getDefaultSelect());
        values.put("defaultLimit", config.getDefaultLimit());
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        return PluginSettingsSection.builder()
                .title("Supabase")
                .description("Configure Supabase PostgREST access for listing and mutating table rows.")
                .fields(List.of(
                        booleanField("enabled", "Enable Supabase",
                                "Allow the model to query and mutate Supabase rows."),
                        textField("projectUrl", "Project URL",
                                "Supabase project URL, for example https://your-project.supabase.co.",
                                "https://your-project.supabase.co"),
                        secretField("apiKey", "API Key",
                                "Supabase service role or anon key. Leave blank to keep the current secret."),
                        textField("defaultSchema", "Default Schema",
                                "Default Postgres schema used for requests.", SupabasePluginConfig.DEFAULT_SCHEMA),
                        textField("defaultTable", "Default Table",
                                "Optional default table name used when the tool call omits table.",
                                "tasks"),
                        textField("defaultSelect", "Default Select",
                                "Default PostgREST select expression for row queries.",
                                SupabasePluginConfig.DEFAULT_SELECT),
                        numberField("defaultLimit", "Default Limit",
                                "Maximum rows returned when limit is omitted.", 1.0, 1000.0, 1.0),
                        booleanField("allowWrite", "Allow Write",
                                "Permit insert_row and update_rows operations."),
                        booleanField("allowDelete", "Allow Delete",
                                "Permit delete_rows operations.")))
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
        SupabasePluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setProjectUrl(readString(values, "projectUrl", config.getProjectUrl()));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setDefaultSchema(readString(values, "defaultSchema", config.getDefaultSchema()));
        config.setDefaultTable(readString(values, "defaultTable", config.getDefaultTable()));
        config.setDefaultSelect(readString(values, "defaultSelect", config.getDefaultSelect()));
        config.setDefaultLimit(readInteger(values, "defaultLimit", config.getDefaultLimit()));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (ACTION_TEST_CONNECTION.equals(actionId)) {
            return testConnection();
        }
        throw new IllegalArgumentException("Unknown Supabase action: " + actionId);
    }

    private PluginActionResult testConnection() {
        SupabasePluginConfig config = configService.getConfig();
        if (!hasText(config.getProjectUrl())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Supabase project URL is not configured.")
                    .build();
        }
        if (!hasText(config.getApiKey())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Supabase API key is not configured.")
                    .build();
        }
        if (!hasText(config.getDefaultTable())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Default Supabase table is not configured.")
                    .build();
        }
        try {
            List<Map<String, Object>> rows = apiClient.selectRows(
                    config.getDefaultTable(),
                    config.getDefaultSchema(),
                    config.getDefaultSelect(),
                    1,
                    null,
                    null,
                    Optional.empty(),
                    Map.of());
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Connected to Supabase. Read access to "
                            + config.getDefaultSchema() + "." + config.getDefaultTable()
                            + " is available (" + rows.size() + " row(s) checked).")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | SupabaseApiException
                | SupabaseTransportException ex) {
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
            throw new IllegalArgumentException("Unknown Supabase settings section: " + sectionKey);
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
