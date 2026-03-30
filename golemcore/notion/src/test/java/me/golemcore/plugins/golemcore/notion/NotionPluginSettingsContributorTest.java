package me.golemcore.plugins.golemcore.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsFieldOption;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugin.api.runtime.RagIngestionService;
import me.golemcore.plugin.api.runtime.model.RagIngestionTargetDescriptor;
import me.golemcore.plugins.golemcore.notion.support.NotionApiClient;
import me.golemcore.plugins.golemcore.notion.support.NotionReindexCoordinator;
import me.golemcore.plugins.golemcore.notion.support.NotionReindexSummary;
import me.golemcore.plugins.golemcore.notion.support.NotionTransportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotionPluginSettingsContributorTest {

    private NotionPluginConfigService configService;
    private RagIngestionService ragIngestionService;
    private NotionApiClient apiClient;
    private NotionReindexCoordinator reindexCoordinator;
    private NotionPluginSettingsContributor contributor;
    private NotionPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(NotionPluginConfigService.class);
        ragIngestionService = mock(RagIngestionService.class);
        apiClient = mock(NotionApiClient.class);
        reindexCoordinator = mock(NotionReindexCoordinator.class);
        contributor = new NotionPluginSettingsContributor(
                configService,
                ragIngestionService,
                apiClient,
                reindexCoordinator);
        config = NotionPluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
        when(ragIngestionService.listInstalledTargets()).thenReturn(List.of(
                new RagIngestionTargetDescriptor(
                        "golemcore/lightrag",
                        "golemcore/lightrag",
                        "LightRAG",
                        null),
                new RagIngestionTargetDescriptor(
                        "acme/raggy",
                        "acme/raggy",
                        "Raggy",
                        null)));
    }

    @Test
    void shouldExposeSafeDefaultsAndFriendlyScheduleOptions() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("https://api.notion.com", section.getValues().get("baseUrl"));
        assertEquals("2026-03-11", section.getValues().get("apiVersion"));
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("", section.getValues().get("rootPageId"));
        assertEquals(30_000, section.getValues().get("timeoutMs"));
        assertEquals(12_000, section.getValues().get("maxReadChars"));
        assertEquals(false, section.getValues().get("localIndexEnabled"));
        assertEquals("disabled", section.getValues().get("reindexSchedulePreset"));
        assertEquals("", section.getValues().get("reindexCronExpression"));
        assertEquals(false, section.getValues().get("ragSyncEnabled"));
        assertEquals("", section.getValues().get("targetRagProviderId"));
        assertEquals("notion", section.getValues().get("ragCorpusId"));
        assertEquals(2, section.getActions().size());
        assertEquals("test-connection", section.getActions().getFirst().getActionId());
        assertEquals("reindex-now", section.getActions().get(1).getActionId());

        PluginSettingsField scheduleField = section.getFields().stream()
                .filter(field -> "reindexSchedulePreset".equals(field.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("select", scheduleField.getType());
        assertEquals(List.of("disabled", "hourly", "every_6_hours", "daily", "weekly", "custom"),
                scheduleField.getOptions().stream().map(PluginSettingsFieldOption::getValue).toList());

        PluginSettingsField ragTargetField = section.getFields().stream()
                .filter(field -> "targetRagProviderId".equals(field.getKey()))
                .findFirst()
                .orElseThrow();
        assertEquals("select", ragTargetField.getType());
        assertEquals(List.of("golemcore/lightrag", "acme/raggy"),
                ragTargetField.getOptions().stream().map(PluginSettingsFieldOption::getValue).toList());
    }

    @Test
    void shouldRoundTripSavedValuesWithoutOverwritingBlankSecret() {
        NotionPluginConfig initialConfig = NotionPluginConfig.builder()
                .apiKey("existing-secret")
                .build();
        initialConfig.normalize();
        NotionPluginConfig persistedConfig = NotionPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://api.notion.com")
                .apiVersion("2026-03-11")
                .apiKey("existing-secret")
                .rootPageId("root-page")
                .timeoutMs(45_000)
                .maxReadChars(8_000)
                .allowWrite(true)
                .allowDelete(false)
                .allowMove(true)
                .allowRename(false)
                .localIndexEnabled(true)
                .reindexSchedulePreset("daily")
                .reindexCronExpression("0 0 3 * * *")
                .ragSyncEnabled(true)
                .targetRagProviderId("golemcore/lightrag")
                .ragCorpusId("team-notes")
                .build();
        persistedConfig.normalize();
        when(configService.getConfig()).thenReturn(initialConfig, persistedConfig);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("baseUrl", "https://api.notion.com");
        values.put("apiVersion", "2026-03-11");
        values.put("apiKey", "");
        values.put("rootPageId", "root-page");
        values.put("timeoutMs", 45_000);
        values.put("maxReadChars", 8_000);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("allowMove", true);
        values.put("allowRename", false);
        values.put("localIndexEnabled", true);
        values.put("reindexSchedulePreset", "daily");
        values.put("reindexCronExpression", "0 0 3 * * *");
        values.put("ragSyncEnabled", true);
        values.put("targetRagProviderId", "golemcore/lightrag");
        values.put("ragCorpusId", "team-notes");

        PluginSettingsSection section = contributor.saveSection("main", values);

        ArgumentCaptor<NotionPluginConfig> captor = ArgumentCaptor.forClass(NotionPluginConfig.class);
        verify(configService).save(captor.capture());
        verify(reindexCoordinator).refreshSchedule();
        NotionPluginConfig saved = captor.getValue();
        assertEquals("existing-secret", saved.getApiKey());
        assertTrue(saved.getAllowWrite());
        assertFalse(saved.getAllowDelete());
        assertTrue(saved.getAllowMove());
        assertFalse(saved.getAllowRename());
        assertTrue(saved.getLocalIndexEnabled());
        assertEquals("daily", saved.getReindexSchedulePreset());
        assertTrue(saved.getRagSyncEnabled());
        assertEquals("golemcore/lightrag", saved.getTargetRagProviderId());

        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("daily", section.getValues().get("reindexSchedulePreset"));
        assertEquals(true, section.getValues().get("ragSyncEnabled"));
        assertEquals("golemcore/lightrag", section.getValues().get("targetRagProviderId"));
    }

    @Test
    void shouldReturnOkWhenConnectionTestSucceeds() {
        config.setApiKey("secret");
        config.setRootPageId("root-page");
        when(apiClient.retrievePageTitle("root-page")).thenReturn("Knowledge Vault");

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Connected to Notion. Root page: Knowledge Vault.", result.getMessage());
        verify(apiClient).retrievePageTitle("root-page");
    }

    @Test
    void shouldReturnErrorWhenApiKeyIsMissing() {
        config.setRootPageId("root-page");

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Notion API key is not configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenRootPageIdIsMissing() {
        config.setApiKey("secret");

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Root page ID is not configured.", result.getMessage());
    }

    @Test
    void shouldReturnErrorWhenConnectionTestFails() {
        config.setApiKey("secret");
        config.setRootPageId("root-page");
        when(apiClient.retrievePageTitle("root-page"))
                .thenThrow(new NotionTransportException("Notion transport failed: timeout", null));

        PluginActionResult result = contributor.executeAction("main", "test-connection", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Connection failed: Notion transport failed: timeout", result.getMessage());
    }

    @Test
    void shouldRunReindexActionWhenRequested() {
        when(reindexCoordinator.reindexNow()).thenReturn(new NotionReindexSummary(4, 7, 3));

        PluginActionResult result = contributor.executeAction("main", "reindex-now", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Reindex completed. Indexed 4 page(s), 7 chunk(s), and synced 3 document(s).",
                result.getMessage());
        verify(reindexCoordinator).reindexNow();
    }

    @Test
    void shouldReturnErrorWhenReindexActionFails() {
        when(reindexCoordinator.reindexNow()).thenThrow(new IllegalStateException("No indexing target is enabled."));

        PluginActionResult result = contributor.executeAction("main", "reindex-now", Map.of());

        assertEquals("error", result.getStatus());
        assertEquals("Reindex failed: No indexing target is enabled.", result.getMessage());
    }
}
