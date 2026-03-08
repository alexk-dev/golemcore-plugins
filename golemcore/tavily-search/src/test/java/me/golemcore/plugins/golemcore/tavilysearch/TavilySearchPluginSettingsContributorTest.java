package me.golemcore.plugins.golemcore.tavilysearch;

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

class TavilySearchPluginSettingsContributorTest {

    private TavilySearchPluginConfigService configService;
    private TavilySearchPluginSettingsContributor contributor;
    private TavilySearchPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(TavilySearchPluginConfigService.class);
        contributor = new TavilySearchPluginSettingsContributor(configService);
        config = TavilySearchPluginConfig.builder()
                .enabled(true)
                .apiKey("tvly-existing")
                .defaultMaxResults(5)
                .defaultTopic("general")
                .defaultSearchDepth("basic")
                .includeAnswer(true)
                .includeRawContent(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("Tavily Search", section.getTitle());
        assertEquals("", section.getValues().get("apiKey"));
        assertEquals(5, section.getValues().get("defaultMaxResults"));
        assertTrue((Boolean) section.getValues().get("includeAnswer"));
    }

    @Test
    void shouldPreserveExistingApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "apiKey", "",
                "defaultMaxResults", "8",
                "defaultTopic", "news",
                "defaultSearchDepth", "advanced",
                "includeAnswer", false,
                "includeRawContent", true));

        ArgumentCaptor<TavilySearchPluginConfig> captor = ArgumentCaptor.forClass(TavilySearchPluginConfig.class);
        verify(configService).save(captor.capture());
        TavilySearchPluginConfig saved = captor.getValue();
        assertEquals("tvly-existing", saved.getApiKey());
        assertFalse(saved.getEnabled());
        assertEquals(8, saved.getDefaultMaxResults());
        assertEquals("news", saved.getDefaultTopic());
        assertEquals("advanced", saved.getDefaultSearchDepth());
        assertFalse(saved.getIncludeAnswer());
        assertTrue(saved.getIncludeRawContent());
    }
}
