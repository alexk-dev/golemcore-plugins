package me.golemcore.plugins.golemcore.slack;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackPluginSettingsContributorTest {

    private SlackPluginConfigService configService;
    private ApplicationEventPublisher eventPublisher;
    private SlackPluginSettingsContributor contributor;
    private SlackPluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(SlackPluginConfigService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        contributor = new SlackPluginSettingsContributor(configService, eventPublisher);
        config = SlackPluginConfig.builder()
                .enabled(true)
                .botToken("xoxb-existing")
                .appToken("xapp-existing")
                .replyInThread(true)
                .allowedUserIds(List.of("U123"))
                .allowedChannelIds(List.of("C123"))
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecrets() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("botToken"));
        assertEquals("", section.getValues().get("appToken"));
        assertEquals("U123", section.getValues().get("allowedUserIds"));
        assertEquals("C123", section.getValues().get("allowedChannelIds"));
    }

    @Test
    void shouldPreserveSecretsWhenBlankValuesAreSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "botToken", "",
                "appToken", "",
                "replyInThread", false,
                "allowedUserIds", "U777, U888",
                "allowedChannelIds", "D123, C999"));

        ArgumentCaptor<SlackPluginConfig> captor = ArgumentCaptor.forClass(SlackPluginConfig.class);
        verify(configService).save(captor.capture());
        SlackPluginConfig saved = captor.getValue();
        assertEquals("xoxb-existing", saved.getBotToken());
        assertEquals("xapp-existing", saved.getAppToken());
        assertTrue(Boolean.FALSE.equals(saved.getEnabled()));
        assertTrue(Boolean.FALSE.equals(saved.getReplyInThread()));
        assertEquals(List.of("U777", "U888"), saved.getAllowedUserIds());
        assertEquals(List.of("D123", "C999"), saved.getAllowedChannelIds());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(SlackRestartEvent.class));
    }

    @Test
    void shouldPublishRestartAction() {
        PluginActionResult result = contributor.executeAction("main", "restart-slack", Map.of());

        assertEquals("ok", result.getStatus());
        assertEquals("Slack restart requested.", result.getMessage());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(SlackRestartEvent.class));
    }
}
