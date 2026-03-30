package me.golemcore.plugins.golemcore.telegram;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.runtime.model.RuntimeConfig;
import me.golemcore.plugin.api.runtime.model.Secret;
import me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram.TelegramTransportReconcileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramPluginSettingsContributorTest {

    private RuntimeConfigService runtimeConfigService;
    private TelegramTransportReconcileService reconcileService;
    private TelegramPluginSettingsContributor contributor;
    private RuntimeConfig config;

    @BeforeEach
    void setUp() {
        runtimeConfigService = mock(RuntimeConfigService.class);
        reconcileService = mock(TelegramTransportReconcileService.class);
        contributor = new TelegramPluginSettingsContributor(runtimeConfigService, reconcileService);
        config = RuntimeConfig.builder()
                .telegram(RuntimeConfig.TelegramConfig.builder().build())
                .voice(RuntimeConfig.VoiceConfig.builder().build())
                .build();
        when(runtimeConfigService.getRuntimeConfig()).thenReturn(config);
        when(runtimeConfigService.getRuntimeConfigForApi()).thenReturn(config);
    }

    @Test
    void shouldExposeTelegramTransportDefaultsFromRuntimeConfig() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals(false, section.getValues().get("enabled"));
        assertEquals("", section.getValues().get("token"));
        assertEquals("polling", section.getValues().get("transportMode"));
        assertEquals("", section.getValues().get("webhookSecretToken"));
        assertEquals("chat", section.getValues().get("conversationScope"));
        assertEquals(true, section.getValues().get("aggregateIncomingMessages"));
        assertEquals(500, section.getValues().get("aggregationDelayMs"));
        assertEquals(true, section.getValues().get("mergeForwardedMessages"));
        assertEquals(true, section.getValues().get("mergeSequentialFragments"));

        PluginSettingsField webhookSecretField = findField(section, "webhookSecretToken");
        assertEquals("text", webhookSecretField.getType());
        assertEquals(true, webhookSecretField.getMasked());
    }

    @Test
    void shouldExposeCopyableWebhookUrlOnlyInWebhookMode() {
        config.getTelegram().setTransportMode("webhook");
        config.getTelegram().setWebhookSecretToken("safe-string-token");

        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("/api/telegram/webhook", section.getValues().get("webhookUrl"));
        PluginSettingsField webhookUrlField = findField(section, "webhookUrl");
        assertEquals("url", webhookUrlField.getType());
        assertEquals(true, webhookUrlField.getReadOnly());
        assertEquals(true, webhookUrlField.getCopyable());
    }

    @Test
    void shouldSaveTransportSettingsAndRequestAsyncReconcile() {
        config.getTelegram().setToken(Secret.of("existing-bot-token"));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("token", "");
        values.put("transportMode", "webhook");
        values.put("webhookSecretToken", "safe-string-token");
        values.put("conversationScope", "thread");
        values.put("aggregateIncomingMessages", false);
        values.put("aggregationDelayMs", 750);
        values.put("mergeForwardedMessages", false);
        values.put("mergeSequentialFragments", false);
        values.put("telegramRespondWithVoice", true);
        values.put("telegramTranscribeIncoming", true);

        PluginSettingsSection savedSection = contributor.saveSection("main", values);

        ArgumentCaptor<RuntimeConfig> captor = ArgumentCaptor.forClass(RuntimeConfig.class);
        verify(runtimeConfigService).updateRuntimeConfig(captor.capture());
        RuntimeConfig saved = captor.getValue();
        assertEquals("existing-bot-token", saved.getTelegram().getToken().getValue());
        assertEquals("webhook", saved.getTelegram().getTransportMode());
        assertEquals("safe-string-token", saved.getTelegram().getWebhookSecretToken());
        assertEquals("thread", saved.getTelegram().getConversationScope());
        assertFalse(saved.getTelegram().getAggregateIncomingMessages());
        assertEquals(750, saved.getTelegram().getAggregationDelayMs());
        assertFalse(saved.getTelegram().getMergeForwardedMessages());
        assertFalse(saved.getTelegram().getMergeSequentialFragments());
        assertTrue(saved.getVoice().getTelegramRespondWithVoice());
        assertTrue(saved.getVoice().getTelegramTranscribeIncoming());

        verify(reconcileService).requestReconcile();
        assertEquals("webhook", savedSection.getValues().get("transportMode"));
        assertEquals("safe-string-token", savedSection.getValues().get("webhookSecretToken"));
    }

    @Test
    void shouldRequestReconcileWhenRestartActionIsTriggered() {
        PluginActionResult result = contributor.executeAction("main", "restart-telegram", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Telegram reconcile requested.", result.getMessage());
        verify(reconcileService).requestReconcile();
    }

    private PluginSettingsField findField(PluginSettingsSection section, String key) {
        return section.getFields().stream()
                .filter(field -> key.equals(field.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field: " + key));
    }
}
