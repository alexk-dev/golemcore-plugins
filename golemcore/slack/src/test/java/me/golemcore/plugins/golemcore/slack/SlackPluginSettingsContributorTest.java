package me.golemcore.plugins.golemcore.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                .build();
        when(configService.getConfig()).thenReturn(config);
    }

    @Test
    void shouldExposeSectionWithBlankSecrets() {
        PluginSettingsSection section = contributor.getSection("main");

        assertEquals("", section.getValues().get("botToken"));
        assertEquals("", section.getValues().get("appToken"));
        assertTrue(!section.getValues().containsKey("allowedUserIds"));
        assertTrue(!section.getValues().containsKey("allowedChannelIds"));
    }

    @Test
    void shouldPreserveSecretsWhenBlankValuesAreSaved() {
        contributor.saveSection("main", Map.of(
                "enabled", false,
                "botToken", "",
                "appToken", "",
                "replyInThread", false));

        ArgumentCaptor<SlackPluginConfig> captor = ArgumentCaptor.forClass(SlackPluginConfig.class);
        verify(configService).save(captor.capture());
        SlackPluginConfig saved = captor.getValue();
        Map<String, Object> serialized = objectMapper.convertValue(saved, Map.class);
        assertEquals("xoxb-existing", saved.getBotToken());
        assertEquals("xapp-existing", saved.getAppToken());
        assertTrue(Boolean.FALSE.equals(saved.getEnabled()));
        assertTrue(Boolean.FALSE.equals(saved.getReplyInThread()));
        assertTrue(!serialized.containsKey("allowedUserIds"));
        assertTrue(!serialized.containsKey("allowedChannelIds"));
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
