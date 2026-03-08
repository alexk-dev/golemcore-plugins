package me.golemcore.plugins.golemcore.perplexitysonar;

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

class PerplexitySonarPluginSettingsContributorTest {

    private PerplexitySonarPluginConfigService configService;
    private PerplexitySonarPluginSettingsContributor contributor;
    private PerplexitySonarPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(PerplexitySonarPluginConfigService.class);
        contributor = new PerplexitySonarPluginSettingsContributor(configService);
        config = PerplexitySonarPluginConfig.builder()
                .enabled(true)
                .apiKey("pplx-existing")
                .defaultModel("sonar")
                .defaultSearchMode("web")
                .returnRelatedQuestions(false)
                .returnImages(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("sonar", section.getValues().get("defaultModel"));
        assertEquals("web", section.getValues().get("defaultSearchMode"));
    }

    @Test
    void shouldPreserveApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "apiKey", "",
                "defaultModel", "sonar-pro",
                "defaultSearchMode", "academic",
                "returnRelatedQuestions", true,
                "returnImages", true));

        ArgumentCaptor<PerplexitySonarPluginConfig> captor = ArgumentCaptor.forClass(PerplexitySonarPluginConfig.class);
        verify(configService).save(captor.capture());
        PerplexitySonarPluginConfig saved = captor.getValue();
        assertEquals("pplx-existing", saved.getApiKey());
        assertFalse(saved.getEnabled());
        assertEquals("sonar-pro", saved.getDefaultModel());
        assertEquals("academic", saved.getDefaultSearchMode());
        assertTrue(saved.getReturnRelatedQuestions());
        assertTrue(saved.getReturnImages());
    }
}
