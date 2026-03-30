package me.golemcore.plugins.golemcore.notion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class NotionPluginConfigTest {

    @Test
    void shouldNormalizeSafeDefaultsAndFriendlySchedulePreset() {
        NotionPluginConfig config = NotionPluginConfig.builder()
                .enabled(null)
                .baseUrl(" https://api.notion.com/ ")
                .apiVersion(" ")
                .timeoutMs(0)
                .maxReadChars(0)
                .allowWrite(null)
                .allowDelete(null)
                .allowMove(null)
                .allowRename(null)
                .localIndexEnabled(null)
                .reindexSchedulePreset(null)
                .reindexCronExpression(null)
                .ragSyncEnabled(null)
                .targetRagProviderId(null)
                .ragCorpusId(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("https://api.notion.com", config.getBaseUrl());
        assertEquals("2026-03-11", config.getApiVersion());
        assertEquals(30_000, config.getTimeoutMs());
        assertEquals(12_000, config.getMaxReadChars());
        assertFalse(config.getAllowWrite());
        assertFalse(config.getAllowDelete());
        assertFalse(config.getAllowMove());
        assertFalse(config.getAllowRename());
        assertFalse(config.getLocalIndexEnabled());
        assertEquals("disabled", config.getReindexSchedulePreset());
        assertEquals("", config.getReindexCronExpression());
        assertFalse(config.getRagSyncEnabled());
        assertEquals("", config.getTargetRagProviderId());
        assertEquals("notion", config.getRagCorpusId());
    }
}
