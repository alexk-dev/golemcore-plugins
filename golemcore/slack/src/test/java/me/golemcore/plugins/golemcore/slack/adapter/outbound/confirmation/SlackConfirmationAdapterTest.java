package me.golemcore.plugins.golemcore.slack.adapter.outbound.confirmation;

import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.ButtonElement;
import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.support.SlackPostedMessage;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackConfirmationAdapterTest {

    private RuntimeConfigService runtimeConfigService;
    private SlackPluginConfigService configService;
    private SlackSocketGateway slackSocketGateway;
    private SlackConfirmationAdapter adapter;

    @BeforeEach
    void setUp() {
        runtimeConfigService = mock(RuntimeConfigService.class);
        configService = mock(SlackPluginConfigService.class);
        slackSocketGateway = mock(SlackSocketGateway.class);
        adapter = new SlackConfirmationAdapter(runtimeConfigService, configService, slackSocketGateway);

        when(runtimeConfigService.isToolConfirmationEnabled()).thenReturn(true);
        when(runtimeConfigService.getToolConfirmationTimeoutSeconds()).thenReturn(5);
        when(slackSocketGateway.isConnected()).thenReturn(true);
        when(configService.getConfig()).thenReturn(SlackPluginConfig.builder()
                .enabled(true)
                .botToken("xoxb-test")
                .appToken("xapp-test")
                .build());
        when(slackSocketGateway.postBlocks(anyString(), any(), anyString(), anyList()))
                .thenReturn(CompletableFuture
                        .completedFuture(new SlackPostedMessage("C123", "1710000000.500", "1710000000.001")));
    }

    @Test
    void shouldResolveApprovalFromInteractiveCallback() {
        CompletableFuture<Boolean> decision = adapter.requestConfirmation("C123::1710000000.001", "shell", "rm -rf");

        String confirmationId = extractActionValue();
        adapter.onConfirmationCallback(new ConfirmationCallbackEvent(
                confirmationId,
                true,
                "C123::1710000000.001",
                "1710000000.500"));

        assertTrue(decision.join());
        verify(slackSocketGateway).updateMessage(
                "xoxb-test",
                "C123",
                "1710000000.500",
                ":white_check_mark: Action confirmed.",
                List.of());
    }

    @Test
    void shouldResolveCancellationFromInteractiveCallback() {
        CompletableFuture<Boolean> decision = adapter.requestConfirmation("C123::1710000000.001", "shell", "rm -rf");

        String confirmationId = extractActionValue();
        adapter.onConfirmationCallback(new ConfirmationCallbackEvent(
                confirmationId,
                false,
                "C123::1710000000.001",
                "1710000000.500"));

        assertFalse(decision.join());
        verify(slackSocketGateway).updateMessage(
                "xoxb-test",
                "C123",
                "1710000000.500",
                ":x: Action cancelled.",
                List.of());
    }

    @Test
    void shouldSkipNonSlackChatIds() {
        assertTrue(adapter.requestConfirmation("telegram-42", "shell", "rm -rf").join());
        verify(slackSocketGateway, never()).postBlocks(anyString(), any(), anyString(), anyList());
    }

    @SuppressWarnings("unchecked")
    private String extractActionValue() {
        ArgumentCaptor<List<LayoutBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(slackSocketGateway).postBlocks(anyString(), any(), anyString(), blocksCaptor.capture());
        ActionsBlock actionsBlock = (ActionsBlock) blocksCaptor.getValue().get(1);
        ButtonElement confirmButton = (ButtonElement) actionsBlock.getElements().get(0);
        return confirmButton.getValue();
    }
}
