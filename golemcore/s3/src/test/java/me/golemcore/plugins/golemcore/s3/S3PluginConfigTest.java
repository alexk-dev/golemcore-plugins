package me.golemcore.plugins.golemcore.s3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class S3PluginConfigTest {

    @Test
    void shouldNormalizeSafeDefaultsAndRootPrefix() {
        S3PluginConfig config = S3PluginConfig.builder()
                .enabled(null)
                .endpoint(" https://storage.example.com/ ")
                .region(" ")
                .bucket(" files ")
                .rootPrefix(" /AI/Uploads/ ")
                .timeoutMs(0)
                .allowInsecureTls(null)
                .maxDownloadBytes(0)
                .maxInlineTextChars(0)
                .autoCreateBucket(null)
                .allowWrite(null)
                .allowDelete(null)
                .allowMove(null)
                .allowCopy(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("https://storage.example.com", config.getEndpoint());
        assertEquals("us-east-1", config.getRegion());
        assertEquals("files", config.getBucket());
        assertEquals("AI/Uploads", config.getRootPrefix());
        assertEquals(30_000, config.getTimeoutMs());
        assertFalse(config.getAllowInsecureTls());
        assertEquals(50 * 1024 * 1024, config.getMaxDownloadBytes());
        assertEquals(12_000, config.getMaxInlineTextChars());
        assertFalse(config.getAutoCreateBucket());
        assertFalse(config.getAllowWrite());
        assertFalse(config.getAllowDelete());
        assertFalse(config.getAllowMove());
        assertFalse(config.getAllowCopy());
    }
}
