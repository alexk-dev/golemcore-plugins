package me.golemcore.plugins.golemcore.slack.adapter.inbound.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.loop.AgentLoop;
import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.extension.model.ContextAttributes;
import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.extension.model.ProgressUpdate;
import me.golemcore.plugin.api.extension.model.ProgressUpdateType;
import me.golemcore.plugin.api.extension.port.outbound.SessionPort;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.support.SlackActionEnvelope;
import me.golemcore.plugins.golemcore.slack.support.SlackActionIds;
import me.golemcore.plugins.golemcore.slack.support.SlackConversationTarget;
import me.golemcore.plugins.golemcore.slack.support.SlackInboundEnvelope;
import me.golemcore.plugins.golemcore.slack.support.SlackPostedMessage;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackAdapterTest {

    private SlackPluginConfigService configService;
    private SlackSocketGateway socketGateway;
    private SessionPort sessionPort;
    private ApplicationEventPublisher eventPublisher;
    private SlackAdapter adapter;
    private SlackPluginConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configService = mock(SlackPluginConfigService.class);
        socketGateway = mock(SlackSocketGateway.class);
        sessionPort = mock(SessionPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        adapter = new SlackAdapter(configService, socketGateway, sessionPort, eventPublisher);
        config = SlackPluginConfig.builder()
                .enabled(true)
                .botToken("xoxb-test")
                .appToken("xapp-test")
                .replyInThread(true)
                .build();
        when(configService.getConfig()).thenReturn(config);
        when(socketGateway.postMessage(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(socketGateway.postBlocks(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new SlackPostedMessage(
                        "C999",
                        "1710000000.500",
                        "1710000000.123")));
        when(socketGateway.updateMessage(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldPublishInboundThreadedMessage() {
        AtomicReference<Message> capturedMessage = new AtomicReference<>();
        adapter.onMessage(capturedMessage::set);
        adapter.start();

        ArgumentCaptor<Consumer<SlackInboundEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), captor.capture(), any());

        captor.getValue().accept(new SlackInboundEnvelope(
                "C123",
                "channel",
                "U123",
                "ship it",
                "1710000000.001",
                "1710000000.001",
                false,
                true));

        Message message = capturedMessage.get();
        assertEquals("slack", message.getChannelType());
        assertEquals("U123", message.getSenderId());
        assertEquals("ship it", message.getContent());
        assertNotEquals("C123", message.getChatId());
        assertEquals("C123::1710000000.001", message.getMetadata().get(ContextAttributes.TRANSPORT_CHAT_ID));
        verify(eventPublisher).publishEvent(any(AgentLoop.InboundMessageEvent.class));
    }

    @Test
    void shouldIgnoreUnauthorizedUsers() {
        config = objectMapper.convertValue(Map.of(
                "enabled", true,
                "botToken", "xoxb-test",
                "appToken", "xapp-test",
                "replyInThread", true,
                "allowedUserIds", List.of("U111")), SlackPluginConfig.class);
        when(configService.getConfig()).thenReturn(config);
        adapter.start();

        ArgumentCaptor<Consumer<SlackInboundEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), captor.capture(), any());

        captor.getValue().accept(new SlackInboundEnvelope(
                "D123",
                "im",
                "U999",
                "hello",
                "1710000000.002",
                null,
                true,
                false));

        verify(eventPublisher).publishEvent(any(AgentLoop.InboundMessageEvent.class));
    }

    @Test
    void shouldIgnoreThreadRepliesWithoutExistingConversation() {
        adapter.start();

        ArgumentCaptor<Consumer<SlackInboundEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), captor.capture(), any());

        captor.getValue().accept(new SlackInboundEnvelope(
                "C123",
                "channel",
                "U123",
                "follow up",
                "1710000000.200",
                "1710000000.001",
                false,
                false));

        verify(eventPublisher, never()).publishEvent(any(AgentLoop.InboundMessageEvent.class));
    }

    @Test
    void shouldNotReportRunningWhenSocketGatewayConnectFails() {
        doThrow(new IllegalStateException("Slack app token is invalid for Socket Mode: invalid_auth"))
                .when(socketGateway)
                .connect(any(), any(), any(), any());

        adapter.start();

        assertFalse(adapter.isRunning());
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), any(), any());
    }

    @Test
    void shouldSendThreadedMessagesUsingTransportChatId() {
        adapter.sendMessage("C999::1710000000.123", "done").join();

        ArgumentCaptor<SlackConversationTarget> targetCaptor = ArgumentCaptor.forClass(SlackConversationTarget.class);
        verify(socketGateway).postMessage(eq("xoxb-test"), targetCaptor.capture(), eq("done"));
        SlackConversationTarget target = targetCaptor.getValue();
        assertEquals("C999", target.channelId());
        assertEquals("1710000000.123", target.threadTs());
        assertTrue(target.threaded());
    }

    @Test
    void shouldSendMessageUsingTransportMetadataWhenAvailable() {
        Message message = Message.builder()
                .chatId("slk_deadbeef")
                .content("hello")
                .metadata(Map.of(ContextAttributes.TRANSPORT_CHAT_ID, "D111"))
                .build();

        adapter.sendMessage(message).join();

        ArgumentCaptor<SlackConversationTarget> targetCaptor = ArgumentCaptor.forClass(SlackConversationTarget.class);
        verify(socketGateway).postMessage(eq("xoxb-test"), targetCaptor.capture(), eq("hello"));
        assertEquals("D111", targetCaptor.getValue().transportChatId());
    }

    @Test
    void shouldPublishConfirmationActionCallbacks() {
        adapter.start();

        ArgumentCaptor<Consumer<SlackActionEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), any(), captor.capture());

        captor.getValue().accept(new SlackActionEnvelope(
                SlackActionIds.CONFIRM_APPROVE,
                "cfm-1",
                "C123",
                "U123",
                "1710000000.300",
                "1710000000.001"));

        ArgumentCaptor<ConfirmationCallbackEvent> eventCaptor = ArgumentCaptor
                .forClass(ConfirmationCallbackEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("cfm-1", eventCaptor.getValue().confirmationId());
        assertTrue(eventCaptor.getValue().approved());
        assertEquals("C123::1710000000.001", eventCaptor.getValue().chatId());
    }

    @Test
    void shouldPublishPlanActionCallbacks() {
        adapter.start();

        ArgumentCaptor<Consumer<SlackActionEnvelope>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(socketGateway).connect(eq("xapp-test"), eq("xoxb-test"), any(), captor.capture());

        captor.getValue().accept(new SlackActionEnvelope(
                SlackActionIds.PLAN_CANCEL,
                "plan-1",
                "D123",
                "U123",
                "1710000000.300",
                null));

        ArgumentCaptor<PlanApprovalCallbackEvent> eventCaptor = ArgumentCaptor
                .forClass(PlanApprovalCallbackEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("plan-1", eventCaptor.getValue().planId());
        assertEquals("cancel", eventCaptor.getValue().action());
        assertEquals("D123", eventCaptor.getValue().chatId());
    }

    @Test
    void shouldSendProgressUpdatesInThread() {
        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.INTENT, "Inspecting the repo", Map.of()))
                .join();

        ArgumentCaptor<SlackConversationTarget> targetCaptor = ArgumentCaptor.forClass(SlackConversationTarget.class);
        verify(socketGateway).postBlocks(eq("xoxb-test"), targetCaptor.capture(),
                eq("Working on this:\nInspecting the repo"),
                eq(List.of()));
        SlackConversationTarget target = targetCaptor.getValue();
        assertEquals("C999", target.channelId());
        assertEquals("1710000000.123", target.threadTs());
        assertTrue(target.threaded());
    }

    @Test
    void shouldUpdateExistingProgressMessage() {
        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.INTENT, "Inspecting the repo", Map.of()))
                .join();

        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.SUMMARY, "Checked 6 files and 2 tests", Map.of()))
                .join();

        verify(socketGateway).updateMessage(
                "xoxb-test",
                "C999",
                "1710000000.500",
                "Progress update:\nChecked 6 files and 2 tests",
                List.of());
        verify(socketGateway, times(1)).postBlocks(any(), any(), any(), any());
    }

    @Test
    void shouldPostNewProgressMessageWhenUpdateFails() {
        when(socketGateway.updateMessage(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("update failed")));

        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.INTENT, "Inspecting the repo", Map.of()))
                .join();

        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.SUMMARY, "Checked 6 files and 2 tests", Map.of()))
                .join();

        verify(socketGateway, times(2)).postBlocks(any(), any(), any(), any());
    }

    @Test
    void shouldForgetTrackedProgressMessageAfterClear() {
        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.INTENT, "Inspecting the repo", Map.of()))
                .join();

        adapter.sendProgressUpdate("C999::1710000000.123", new ProgressUpdate(ProgressUpdateType.CLEAR, "", Map.of()))
                .join();

        adapter.sendProgressUpdate(
                "C999::1710000000.123",
                new ProgressUpdate(ProgressUpdateType.SUMMARY, "Checked 6 files and 2 tests", Map.of()))
                .join();

        verify(socketGateway, times(2)).postBlocks(any(), any(), any(), any());
        verify(socketGateway, never()).updateMessage(any(), any(), any(), any(), any());
    }
}
