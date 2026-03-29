package me.golemcore.plugins.golemcore.obsidian;

import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsidianPluginSettingsContributorTest {

    private ObsidianPluginConfigService configService;
    private ObsidianPluginSettingsContributor contributor;
    private ObsidianPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(ObsidianPluginConfigService.class);
        contributor = new ObsidianPluginSettingsContributor(configService);
        config = ObsidianPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://127.0.0.1:27124")
                .apiKey("existing-secret")
                .timeoutMs(45_000)
                .allowInsecureTls(true)
                .defaultSearchContextLength(120)
                .maxReadChars(8_000)
                .allowWrite(true)
                .allowDelete(false)
                .allowMove(true)
                .allowRename(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretAndSafeDefaults() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("apiKey"));
        assertEquals(false, section.getValues().get("allowWrite"));
        assertEquals(false, section.getValues().get("allowDelete"));
        assertEquals(false, section.getValues().get("allowMove"));
        assertEquals(false, section.getValues().get("allowRename"));
    }

    @Test
    void shouldPreserveApiKeyWhenBlankSecretIsSaved() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enabled", true);
        values.put("baseUrl", "https://127.0.0.1:27124");
        values.put("apiKey", "");
        values.put("timeoutMs", 45_000);
        values.put("allowInsecureTls", true);
        values.put("defaultSearchContextLength", 120);
        values.put("maxReadChars", 8_000);
        values.put("allowWrite", true);
        values.put("allowDelete", false);
        values.put("allowMove", true);
        values.put("allowRename", false);

        contributor.saveSection("main", values);

        ArgumentCaptor<ObsidianPluginConfig> captor = ArgumentCaptor.forClass(ObsidianPluginConfig.class);
        verify(configService).save(captor.capture());
        assertEquals("existing-secret", captor.getValue().getApiKey());
    }
}
