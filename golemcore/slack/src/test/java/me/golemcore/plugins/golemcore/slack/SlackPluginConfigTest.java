package me.golemcore.plugins.golemcore.slack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackPluginConfigTest {

    @Test
    void shouldNormalizeDefaultsAndIdentifiers() {
        SlackPluginConfig config = SlackPluginConfig.builder()
                .enabled(null)
                .replyInThread(null)
                .botToken("  ")
                .appToken(" xapp-token ")
                .allowedUserIds(List.of(" U123 ", "", "U123", "U999"))
                .allowedChannelIds(List.of(" C123 ", "C123", "D456"))
                .build();

        config.normalize();

        assertTrue(Boolean.FALSE.equals(config.getEnabled()));
        assertTrue(Boolean.TRUE.equals(config.getReplyInThread()));
        assertNull(config.getBotToken());
        assertEquals("xapp-token", config.getAppToken());
        assertEquals(List.of("U123", "U999"), config.getAllowedUserIds());
        assertEquals(List.of("C123", "D456"), config.getAllowedChannelIds());
    }
}
