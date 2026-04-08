package me.golemcore.plugins.golemcore.nextcloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NextcloudPluginConfigTest {

    @Test
    void shouldNormalizeSafeDefaultsAndRootSandbox() {
        NextcloudPluginConfig config = NextcloudPluginConfig.builder()
                .enabled(null)
                .baseUrl(" https://cloud.example.com/ ")
                .username(" alex ")
                .rootPath(" /AI/../AI Docs/ ")
                .timeoutMs(0)
                .allowInsecureTls(null)
                .maxDownloadBytes(0)
                .maxInlineTextChars(0)
                .allowWrite(null)
                .allowDelete(null)
                .allowMove(null)
                .allowCopy(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("https://cloud.example.com", config.getBaseUrl());
        assertEquals("alex", config.getUsername());
        assertEquals("AI Docs", config.getRootPath());
        assertEquals(30_000, config.getTimeoutMs());
        assertFalse(config.getAllowInsecureTls());
        assertEquals(50 * 1024 * 1024, config.getMaxDownloadBytes());
        assertEquals(12_000, config.getMaxInlineTextChars());
        assertFalse(config.getAllowWrite());
        assertFalse(config.getAllowDelete());
        assertFalse(config.getAllowMove());
        assertFalse(config.getAllowCopy());
    }
}
