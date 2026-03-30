package me.golemcore.plugins.golemcore.notion;

import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsAction;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsFieldOption;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugin.api.runtime.RagIngestionService;
import me.golemcore.plugin.api.runtime.model.RagIngestionTargetDescriptor;
import me.golemcore.plugins.golemcore.notion.support.NotionApiClient;
import me.golemcore.plugins.golemcore.notion.support.NotionApiException;
import me.golemcore.plugins.golemcore.notion.support.NotionReindexCoordinator;
import me.golemcore.plugins.golemcore.notion.support.NotionReindexSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionTransportException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotionPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";
    private static final String ACTION_TEST_CONNECTION = "test-connection";
    private static final String ACTION_REINDEX_NOW = "reindex-now";

    private final NotionPluginConfigService configService;
    private final RagIngestionService ragIngestionService;
    private final NotionApiClient apiClient;
    private final NotionReindexCoordinator reindexCoordinator;

    @Override
    public String getPluginId() {
        return NotionPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(NotionPluginConfigService.PLUGIN_ID)
                .pluginName("notion")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Notion")
                .description("Notion vault connection, local search, and external RAG sync behavior.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(38)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        NotionPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        values.put("baseUrl", config.getBaseUrl());
        values.put("apiVersion", config.getApiVersion());
        values.put("apiKey", "");
        values.put("rootPageId", config.getRootPageId());
        values.put("timeoutMs", config.getTimeoutMs());
        values.put("maxReadChars", config.getMaxReadChars());
        values.put("allowWrite", Boolean.TRUE.equals(config.getAllowWrite()));
        values.put("allowDelete", Boolean.TRUE.equals(config.getAllowDelete()));
        values.put("allowMove", Boolean.TRUE.equals(config.getAllowMove()));
        values.put("allowRename", Boolean.TRUE.equals(config.getAllowRename()));
        values.put("localIndexEnabled", Boolean.TRUE.equals(config.getLocalIndexEnabled()));
        values.put("reindexSchedulePreset", config.getReindexSchedulePreset());
        values.put("reindexCronExpression", config.getReindexCronExpression());
        values.put("ragSyncEnabled", Boolean.TRUE.equals(config.getRagSyncEnabled()));
        values.put("targetRagProviderId", config.getTargetRagProviderId());
        values.put("ragCorpusId", config.getRagCorpusId());

        return PluginSettingsSection.builder()
                .title("Notion")
                .description("Configure Notion vault access, local full-text indexing, and optional external RAG sync.")
                .fields(List.of(
                        booleanField("enabled", "Enable Notion",
                                "Allow tools to use the Notion vault integration."),
                        textField("baseUrl", "Base URL", "Notion API base URL.", "https://api.notion.com"),
                        textField("apiVersion", "API Version", "Notion-Version header used for requests.",
                                NotionPluginConfig.DEFAULT_API_VERSION),
                        PluginSettingsField.builder()
                                .key("apiKey")
                                .type("secret")
                                .label("API Key")
                                .description("Leave blank to keep the current secret.")
                                .placeholder("secret_...")
                                .build(),
                        textField("rootPageId", "Root Page ID",
                                "Page ID that acts as the pseudo-vault root for traversal and CRUD.",
                                "page-id"),
                        numberField("timeoutMs", "Request Timeout (ms)",
                                "Timeout for Notion API requests.", 1_000.0, 300_000.0, 1_000.0),
                        numberField("maxReadChars", "Max Read Chars",
                                "Maximum number of characters returned when reading a page.", 1.0, null, 1.0),
                        booleanField("allowWrite", "Allow Write",
                                "Permit tools to create or update pages."),
                        booleanField("allowDelete", "Allow Delete",
                                "Permit tools to archive pages."),
                        booleanField("allowMove", "Allow Move",
                                "Permit tools to move pages to a new parent."),
                        booleanField("allowRename", "Allow Rename",
                                "Permit tools to rename pages."),
                        booleanField("localIndexEnabled", "Enable Local Index",
                                "Maintain a local full-text index for search_notes."),
                        PluginSettingsField.builder()
                                .key("reindexSchedulePreset")
                                .type("select")
                                .label("Reindex Schedule")
                                .description("Choose a friendly preset or switch to custom cron.")
                                .options(List.of(
                                        option("disabled", "Disabled"),
                                        option("hourly", "Every hour"),
                                        option("every_6_hours", "Every 6 hours"),
                                        option("daily", "Daily"),
                                        option("weekly", "Weekly"),
                                        option("custom", "Custom cron")))
                                .build(),
                        textField("reindexCronExpression", "Custom Cron Expression",
                                "Used only when Reindex Schedule is set to Custom.", "0 0 * * * *"),
                        booleanField("ragSyncEnabled", "Enable External RAG Sync",
                                "Push Notion documents to a compatible external RAG target."),
                        PluginSettingsField.builder()
                                .key("targetRagProviderId")
                                .type("select")
                                .label("Target RAG Provider")
                                .description("Installed compatible RAG provider used as the sync target.")
                                .options(ragIngestionService.listInstalledTargets().stream()
                                        .map(this::providerOption)
                                        .toList())
                                .build(),
                        textField("ragCorpusId", "RAG Corpus ID",
                                "Stable corpus or namespace used when syncing Notion documents.", "notion")))
                .values(values)
                .actions(List.of(PluginSettingsAction.builder()
                        .actionId(ACTION_TEST_CONNECTION)
                        .label("Test Connection")
                        .variant("secondary")
                        .build(),
                        PluginSettingsAction.builder()
                                .actionId(ACTION_REINDEX_NOW)
                                .label("Reindex Now")
                                .variant("secondary")
                                .build()))
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        NotionPluginConfig config = configService.getConfig();
        config.setEnabled(readBoolean(values, "enabled", false));
        config.setBaseUrl(readString(values, "baseUrl", config.getBaseUrl()));
        config.setApiVersion(readString(values, "apiVersion", config.getApiVersion()));
        String apiKey = readString(values, "apiKey", null);
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKey(apiKey);
        }
        config.setRootPageId(readString(values, "rootPageId", config.getRootPageId()));
        config.setTimeoutMs(readInteger(values, "timeoutMs", config.getTimeoutMs()));
        config.setMaxReadChars(readInteger(values, "maxReadChars", config.getMaxReadChars()));
        config.setAllowWrite(readBoolean(values, "allowWrite", false));
        config.setAllowDelete(readBoolean(values, "allowDelete", false));
        config.setAllowMove(readBoolean(values, "allowMove", false));
        config.setAllowRename(readBoolean(values, "allowRename", false));
        config.setLocalIndexEnabled(readBoolean(values, "localIndexEnabled", false));
        config.setReindexSchedulePreset(readString(values, "reindexSchedulePreset", config.getReindexSchedulePreset()));
        config.setReindexCronExpression(readString(values, "reindexCronExpression",
                config.getReindexCronExpression()));
        config.setRagSyncEnabled(readBoolean(values, "ragSyncEnabled", false));
        config.setTargetRagProviderId(readString(values, "targetRagProviderId",
                config.getTargetRagProviderId()));
        config.setRagCorpusId(readString(values, "ragCorpusId", config.getRagCorpusId()));
        configService.save(config);
        reindexCoordinator.refreshSchedule();
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        if (ACTION_TEST_CONNECTION.equals(actionId)) {
            return testConnection();
        }
        if (ACTION_REINDEX_NOW.equals(actionId)) {
            return reindexNow();
        }
        throw new IllegalArgumentException("Unknown Notion action: " + actionId);
    }

    private PluginActionResult testConnection() {
        NotionPluginConfig config = configService.getConfig();
        if (!hasText(config.getApiKey())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Notion API key is not configured.")
                    .build();
        }
        if (!hasText(config.getRootPageId())) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Root page ID is not configured.")
                    .build();
        }
        try {
            String title = apiClient.retrievePageTitle(config.getRootPageId());
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Connected to Notion. Root page: " + title + ".")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | NotionApiException | NotionTransportException ex) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Connection failed: " + ex.getMessage())
                    .build();
        }
    }

    private PluginActionResult reindexNow() {
        try {
            NotionReindexSummary summary = reindexCoordinator.reindexNow();
            return PluginActionResult.builder()
                    .status("ok")
                    .message("Reindex completed. Indexed " + summary.pagesIndexed()
                            + " page(s), " + summary.chunksIndexed() + " chunk(s), and synced "
                            + summary.documentsSynced() + " document(s).")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return PluginActionResult.builder()
                    .status("error")
                    .message("Reindex failed: " + ex.getMessage())
                    .build();
        }
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown Notion settings section: " + sectionKey);
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

    private PluginSettingsField numberField(
            String key,
            String label,
            String description,
            Double min,
            Double max,
            Double step) {
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

    private PluginSettingsFieldOption option(String value, String label) {
        return PluginSettingsFieldOption.builder().value(value).label(label).build();
    }

    private PluginSettingsFieldOption providerOption(RagIngestionTargetDescriptor descriptor) {
        String label = descriptor.displayName() != null && !descriptor.displayName().isBlank()
                ? descriptor.displayName()
                : descriptor.providerId();
        return PluginSettingsFieldOption.builder()
                .value(descriptor.providerId())
                .label(label)
                .description(descriptor.pluginId())
                .build();
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
