package me.golemcore.plugins.golemcore.slack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackPluginConfigTest {

    @Test
    void shouldNormalizeDefaultsAndSecrets() {
        SlackPluginConfig config = SlackPluginConfig.builder()
                .enabled(null)
                .replyInThread(null)
                .botToken("  ")
                .appToken(" xapp-token ")
                .build();

        config.normalize();

        assertTrue(Boolean.FALSE.equals(config.getEnabled()));
        assertTrue(Boolean.TRUE.equals(config.getReplyInThread()));
        assertNull(config.getBotToken());
        assertEquals("xapp-token", config.getAppToken());
    }
}
