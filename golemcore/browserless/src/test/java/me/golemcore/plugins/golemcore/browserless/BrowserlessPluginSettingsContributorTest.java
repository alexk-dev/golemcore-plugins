package me.golemcore.plugins.golemcore.browserless;

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

class BrowserlessPluginSettingsContributorTest {

    private BrowserlessPluginConfigService configService;
    private BrowserlessPluginSettingsContributor contributor;
    private BrowserlessPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(BrowserlessPluginConfigService.class);
        contributor = new BrowserlessPluginSettingsContributor(configService);
        config = BrowserlessPluginConfig.builder()
                .enabled(true)
                .apiKey("bl_existing")
                .baseUrl("https://production-sfo.browserless.io")
                .defaultFormat("markdown")
                .bestAttempt(false)
                .gotoWaitUntil("networkidle2")
                .gotoTimeoutMs(30000)
                .timeoutMs(30000)
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecretField() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("apiKey"));
        assertEquals("https://production-sfo.browserless.io", section.getValues().get("baseUrl"));
        assertEquals("markdown", section.getValues().get("defaultFormat"));
        assertEquals("networkidle2", section.getValues().get("gotoWaitUntil"));
    }

    @Test
    void shouldPreserveApiKeyWhenBlankSecretIsSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "apiKey", "",
                "baseUrl", "https://custom.browserless.example",
                "defaultFormat", "pdf",
                "bestAttempt", true,
                "gotoWaitUntil", "load",
                "gotoTimeoutMs", "45000",
                "timeoutMs", 60000));

        ArgumentCaptor<BrowserlessPluginConfig> captor = ArgumentCaptor.forClass(BrowserlessPluginConfig.class);
        verify(configService).save(captor.capture());
        BrowserlessPluginConfig saved = captor.getValue();
        assertEquals("bl_existing", saved.getApiKey());
        assertFalse(saved.getEnabled());
        assertEquals("https://custom.browserless.example", saved.getBaseUrl());
        assertEquals("pdf", saved.getDefaultFormat());
        assertTrue(saved.getBestAttempt());
        assertEquals("load", saved.getGotoWaitUntil());
        assertEquals(45000, saved.getGotoTimeoutMs());
        assertEquals(60000, saved.getTimeoutMs());
    }
}
