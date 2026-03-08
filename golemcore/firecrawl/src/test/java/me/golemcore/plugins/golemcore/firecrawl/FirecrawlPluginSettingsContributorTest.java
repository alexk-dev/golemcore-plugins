package me.golemcore.plugins.golemcore.firecrawl;

import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirecrawlPluginSettingsContributorTest {

    private FirecrawlPluginConfigService configService;
    private FirecrawlPluginSettingsContributor contributor;
    private FirecrawlPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(FirecrawlPluginConfigService.class);
        contributor = new FirecrawlPluginSettingsContributor(configService);
        config = FirecrawlPluginConfig.builder()
                .enabled(true)
                .apiKey("fc-existing")
                .defaultFormat("markdown")
                .onlyMainContent(true)
                .maxAgeMs(60000)
                .timeoutMs(30000)
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("markdown", section.getValues().get("defaultFormat"));
        assertTrue((Boolean) section.getValues().get("onlyMainContent"));
    }

    @Test
    void shouldPreserveApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "apiKey", "",
                "defaultFormat", "summary",
                "onlyMainContent", false,
                "maxAgeMs", "120000",
                "timeoutMs", 45000));

        ArgumentCaptor<FirecrawlPluginConfig> captor = ArgumentCaptor.forClass(FirecrawlPluginConfig.class);
        verify(configService).save(captor.capture());
        FirecrawlPluginConfig saved = captor.getValue();
        assertEquals("fc-existing", saved.getApiKey());
        assertFalse(saved.getEnabled());
        assertEquals("summary", saved.getDefaultFormat());
        assertFalse(saved.getOnlyMainContent());
        assertEquals(120000, saved.getMaxAgeMs());
        assertEquals(45000, saved.getTimeoutMs());
    }
}
