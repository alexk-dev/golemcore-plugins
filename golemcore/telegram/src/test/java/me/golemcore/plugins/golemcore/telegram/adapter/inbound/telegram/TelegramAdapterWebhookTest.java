package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.runtime.UserPreferencesService;
import me.golemcore.plugin.api.runtime.i18n.MessageService;
import me.golemcore.plugin.api.runtime.model.RuntimeConfig;
import me.golemcore.plugin.api.runtime.security.AllowlistValidator;
import me.golemcore.plugins.golemcore.telegram.service.TelegramSessionService;
import me.golemcore.plugin.api.extension.port.inbound.CommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramAdapterWebhookTest {

    private TelegramAdapter adapter;
    private TelegramBotsLongPollingApplication botsApplication;
    private TelegramSessionService telegramSessionService;
    private Consumer<Message> messageHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);
        when(runtimeConfigService.isTelegramEnabled()).thenReturn(true);
        when(runtimeConfigService.getTelegramToken()).thenReturn("test-token");
        when(runtimeConfigService.isTelegramTranscribeIncomingEnabled()).thenReturn(false);
        when(runtimeConfigService.getRuntimeConfig()).thenReturn(RuntimeConfig.builder()
                .telegram(RuntimeConfig.TelegramConfig.builder()
                        .enabled(true)
                        .transportMode("webhook")
                        .conversationScope("chat")
                        .aggregateIncomingMessages(false)
                        .build())
                .build());

        AllowlistValidator allowlistValidator = mock(AllowlistValidator.class);
        when(allowlistValidator.isAllowed("telegram", "123")).thenReturn(true);

        botsApplication = mock(TelegramBotsLongPollingApplication.class);
        telegramSessionService = mock(TelegramSessionService.class);
        when(telegramSessionService.resolveActiveConversationKey(anyString())).thenReturn("conv-webhook");

        adapter = new TelegramAdapter(
                runtimeConfigService,
                allowlistValidator,
                mock(ApplicationEventPublisher.class),
                botsApplication,
                mock(UserPreferencesService.class),
                mock(MessageService.class),
                new TestObjectProvider<>(mock(CommandPort.class)),
                mock(TelegramVoiceHandler.class),
                mock(TelegramMenuHandler.class),
                telegramSessionService,
                new TelegramInboundAssembler());
        adapter.setTelegramClient(mock(TelegramClient.class));

        messageHandler = mock(Consumer.class);
        adapter.onMessage(messageHandler);
    }

    @Test
    void shouldStartInWebhookModeWithoutRegisteringPollingBot() throws Exception {
        adapter.start();

        assertTrue(adapter.isRunning());
        verify(botsApplication, never()).registerBot(anyString(), any());
    }

    @Test
    void shouldConsumeWebhookUpdateJsonThroughSameInboundPath() {
        String updateJson = """
                {
                  "update_id": 1,
                  "message": {
                    "message_id": 77,
                    "date": 1711800000,
                    "chat": { "id": 100, "type": "private" },
                    "from": { "id": 123, "is_bot": false, "first_name": "Alice" },
                    "text": "Webhook hello"
                  }
                }
                """;

        CompletableFuture<Void> future = adapter.acceptUpdate(updateJson);
        future.join();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageHandler).accept(captor.capture());
        assertEquals("Webhook hello", captor.getValue().getContent());
        assertEquals("conv-webhook", captor.getValue().getChatId());
        assertEquals("100", captor.getValue().getMetadata().get("session.transport.chat.id"));
    }
}
