package me.golemcore.plugins.golemcore.pinchtab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PinchTabPluginConfigTest {

    @Test
    void shouldNormalizeDefaultsAndClampValues() {
        PinchTabPluginConfig config = PinchTabPluginConfig.builder()
                .enabled(null)
                .baseUrl(" http://localhost:9867/ ")
                .apiToken("   ")
                .defaultInstanceId("   ")
                .requestTimeoutMs(999_999)
                .defaultSnapshotFilter("unexpected")
                .defaultSnapshotFormat("unexpected")
                .defaultTextMode("unexpected")
                .defaultWaitFor("unexpected")
                .defaultBlockImages(null)
                .defaultScreenshotQuality(999)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("http://localhost:9867", config.getBaseUrl());
        assertNull(config.getApiToken());
        assertNull(config.getDefaultInstanceId());
        assertEquals(300_000, config.getRequestTimeoutMs());
        assertEquals("interactive", config.getDefaultSnapshotFilter());
        assertEquals("compact", config.getDefaultSnapshotFormat());
        assertEquals("readability", config.getDefaultTextMode());
        assertEquals("dom", config.getDefaultWaitFor());
        assertFalse(config.getDefaultBlockImages());
        assertEquals(100, config.getDefaultScreenshotQuality());
    }
}
