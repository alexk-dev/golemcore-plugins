package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PinchTabPluginSettingsContributorTest {

    private PinchTabPluginConfigService configService;
    private PinchTabPluginSettingsContributor contributor;

    @BeforeEach
    void setUp() {
        configService = mock(PinchTabPluginConfigService.class);
        contributor = new PinchTabPluginSettingsContributor(configService);
    }

    @Test
    void shouldExposeConfiguredValues() {
        PinchTabPluginConfig config = PinchTabPluginConfig.builder()
                .enabled(true)
                .baseUrl("http://localhost:9999")
                .defaultInstanceId("inst_123")
                .requestTimeoutMs(45_000)
                .defaultWaitFor("networkidle")
                .defaultBlockImages(true)
                .defaultSnapshotFilter("all")
                .defaultSnapshotFormat("json")
                .defaultTextMode("raw")
                .defaultScreenshotQuality(70)
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);

        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("PinchTab", section.getTitle());
        assertEquals(true, section.getValues().get("enabled"));
        assertEquals("http://localhost:9999", section.getValues().get("baseUrl"));
        assertEquals("inst_123", section.getValues().get("defaultInstanceId"));
        assertEquals(45_000, section.getValues().get("requestTimeoutMs"));
        assertEquals("networkidle", section.getValues().get("defaultWaitFor"));
        assertEquals(true, section.getValues().get("defaultBlockImages"));
        assertEquals("all", section.getValues().get("defaultSnapshotFilter"));
        assertEquals("json", section.getValues().get("defaultSnapshotFormat"));
        assertEquals("raw", section.getValues().get("defaultTextMode"));
        assertEquals(70, section.getValues().get("defaultScreenshotQuality"));
    }

    @Test
    void shouldSaveUpdatedValues() {
        PinchTabPluginConfig config = PinchTabPluginConfig.builder().build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("baseUrl", "http://localhost:9870/");
        values.put("apiToken", "secret");
        values.put("defaultInstanceId", "inst_999");
        values.put("requestTimeoutMs", 40_000);
        values.put("defaultWaitFor", "networkidle");
        values.put("defaultBlockImages", true);
        values.put("defaultSnapshotFilter", "all");
        values.put("defaultSnapshotFormat", "json");
        values.put("defaultTextMode", "raw");
        values.put("defaultScreenshotQuality", 61);

        contributor.saveSection("main", values);

        ArgumentCaptor<PinchTabPluginConfig> captor = ArgumentCaptor.forClass(PinchTabPluginConfig.class);
        verify(configService).save(captor.capture());

        PinchTabPluginConfig saved = captor.getValue();
        saved.normalize();
        assertTrue(saved.getEnabled());
        assertEquals("http://localhost:9870", saved.getBaseUrl());
        assertEquals("secret", saved.getApiToken());
        assertEquals("inst_999", saved.getDefaultInstanceId());
        assertEquals(40_000, saved.getRequestTimeoutMs());
        assertEquals("networkidle", saved.getDefaultWaitFor());
        assertTrue(saved.getDefaultBlockImages());
        assertEquals("all", saved.getDefaultSnapshotFilter());
        assertEquals("json", saved.getDefaultSnapshotFormat());
        assertEquals("raw", saved.getDefaultTextMode());
        assertEquals(61, saved.getDefaultScreenshotQuality());
    }
}
