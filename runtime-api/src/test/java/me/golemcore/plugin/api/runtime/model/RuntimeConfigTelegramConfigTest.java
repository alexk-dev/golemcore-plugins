package me.golemcore.plugin.api.runtime.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigTelegramConfigTest {

    @Test
    void shouldDefaultTelegramTransportAndAggregationSettings() {
        RuntimeConfig.TelegramConfig config = RuntimeConfig.TelegramConfig.builder().build();

        assertEquals("polling", config.getTransportMode());
        assertNull(config.getWebhookSecretToken());
        assertEquals("chat", config.getConversationScope());
        assertTrue(config.getAggregateIncomingMessages());
        assertEquals(500, config.getAggregationDelayMs());
        assertTrue(config.getMergeForwardedMessages());
        assertTrue(config.getMergeSequentialFragments());
    }

    @Test
    void shouldRetainExplicitTelegramTransportAndAggregationSettings() {
        RuntimeConfig.TelegramConfig config = RuntimeConfig.TelegramConfig.builder()
                .transportMode("webhook")
                .webhookSecretToken("secret-token")
                .conversationScope("thread")
                .aggregateIncomingMessages(false)
                .aggregationDelayMs(750)
                .mergeForwardedMessages(false)
                .mergeSequentialFragments(false)
                .build();

        assertEquals("webhook", config.getTransportMode());
        assertEquals("secret-token", config.getWebhookSecretToken());
        assertEquals("thread", config.getConversationScope());
        assertEquals(false, config.getAggregateIncomingMessages());
        assertEquals(750, config.getAggregationDelayMs());
        assertEquals(false, config.getMergeForwardedMessages());
        assertEquals(false, config.getMergeSequentialFragments());
    }
}
