package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.telegram.service.TelegramSessionService;
import me.golemcore.plugin.api.runtime.UserPreferencesService;
import me.golemcore.plugin.api.runtime.i18n.MessageService;
import me.golemcore.plugin.api.extension.port.inbound.CommandPort;
import me.golemcore.plugin.api.runtime.security.AllowlistValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramAdapterCallbackTest {

    private static final String CHAT_ID_100 = "100";

    private TelegramAdapter adapter;
    private ApplicationEventPublisher eventPublisher;
    private TelegramClient telegramClient;
    private TelegramSessionService telegramSessionService;
    private Consumer<Message> messageHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        telegramClient = mock(TelegramClient.class);

        RuntimeConfigService runtimeConfigService = mock(RuntimeConfigService.class);
        when(runtimeConfigService.isTelegramEnabled()).thenReturn(true);
        when(runtimeConfigService.getTelegramToken()).thenReturn("test-token");
        AllowlistValidator allowlistValidator = mock(AllowlistValidator.class);
        when(allowlistValidator.isAllowed("telegram", "123")).thenReturn(true);
        telegramSessionService = mock(TelegramSessionService.class);
        when(telegramSessionService.resolveActiveConversationKey(CHAT_ID_100)).thenReturn("conv-active");

        adapter = new TelegramAdapter(
                runtimeConfigService,
                allowlistValidator,
                eventPublisher,
                mock(TelegramBotsLongPollingApplication.class),
                mock(UserPreferencesService.class),
                mock(MessageService.class),
                new TestObjectProvider<>(mock(CommandPort.class)),
                mock(TelegramVoiceHandler.class),
                mock(TelegramMenuHandler.class),
                telegramSessionService,
                new TelegramInboundAssembler());
        adapter.setTelegramClient(telegramClient);

        messageHandler = mock(Consumer.class);
        adapter.onMessage(messageHandler);
    }

    @Test
    void shouldPublishConfirmationEventOnApproval() throws Exception {
        Update update = createCallbackUpdate(CHAT_ID_100, 42, "confirm:abc123:yes");

        adapter.consume(update);

        ArgumentCaptor<ConfirmationCallbackEvent> captor = ArgumentCaptor.forClass(ConfirmationCallbackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ConfirmationCallbackEvent event = captor.getValue();

        assertEquals("abc123", event.confirmationId());
        assertTrue(event.approved());
        assertEquals(CHAT_ID_100, event.chatId());
        assertEquals("42", event.messageId());
        verify(telegramClient).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void shouldPublishConfirmationEventOnDenial() {
        Update update = createCallbackUpdate("200", 99, "confirm:def456:no");

        adapter.consume(update);

        ArgumentCaptor<ConfirmationCallbackEvent> captor = ArgumentCaptor.forClass(ConfirmationCallbackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ConfirmationCallbackEvent event = captor.getValue();

        assertEquals("def456", event.confirmationId());
        assertFalse(event.approved());
        assertEquals("200", event.chatId());
        assertEquals("99", event.messageId());
    }

    @Test
    void shouldIgnoreInvalidCallbackFormat_tooParts() {
        Update update = createCallbackUpdate(CHAT_ID_100, 1, "confirm:abc");

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(ConfirmationCallbackEvent.class));
    }

    @Test
    void shouldIgnoreInvalidCallbackFormat_tooManyParts() {
        Update update = createCallbackUpdate(CHAT_ID_100, 1, "confirm:a:b:c");

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(ConfirmationCallbackEvent.class));
    }

    @Test
    void shouldNotPublishForNonConfirmCallback() {
        Update update = createCallbackUpdate(CHAT_ID_100, 1, "lang:en");

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(ConfirmationCallbackEvent.class));
    }

    @Test
    void shouldIgnoreCallbackWithoutMessage() {
        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.getMessage()).thenReturn(null);

        Update update = mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.hasMessage()).thenReturn(false);
        when(update.getCallbackQuery()).thenReturn(callback);

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(ConfirmationCallbackEvent.class));
    }

    @Test
    void shouldTreatUnknownResponseAsNotApproved() {
        Update update = createCallbackUpdate(CHAT_ID_100, 42, "confirm:abc123:maybe");

        adapter.consume(update);

        ArgumentCaptor<ConfirmationCallbackEvent> captor = ArgumentCaptor.forClass(ConfirmationCallbackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertFalse(captor.getValue().approved());
    }

    // ===== Plan callbacks =====

    @Test
    void shouldPublishPlanApprovalEvent() throws Exception {
        Update update = createCallbackUpdate(CHAT_ID_100, 42, "plan:plan-abc:approve");

        adapter.consume(update);

        ArgumentCaptor<PlanApprovalCallbackEvent> captor = ArgumentCaptor.forClass(PlanApprovalCallbackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PlanApprovalCallbackEvent event = captor.getValue();

        assertEquals("plan-abc", event.planId());
        assertEquals("approve", event.action());
        assertEquals(CHAT_ID_100, event.chatId());
        assertEquals("42", event.messageId());
        verify(telegramClient).execute(any(AnswerCallbackQuery.class));
    }

    @Test
    void shouldPublishPlanCancelEvent() {
        Update update = createCallbackUpdate("200", 99, "plan:plan-xyz:cancel");

        adapter.consume(update);

        ArgumentCaptor<PlanApprovalCallbackEvent> captor = ArgumentCaptor.forClass(PlanApprovalCallbackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PlanApprovalCallbackEvent event = captor.getValue();

        assertEquals("plan-xyz", event.planId());
        assertEquals("cancel", event.action());
    }

    @Test
    void shouldIgnoreInvalidPlanCallbackFormat() {
        Update update = createCallbackUpdate(CHAT_ID_100, 1, "plan:abc");

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(PlanApprovalCallbackEvent.class));
    }

    @Test
    void shouldIgnorePlanCallbackWithTooManyParts() {
        Update update = createCallbackUpdate(CHAT_ID_100, 1, "plan:a:b:c");

        adapter.consume(update);

        verify(eventPublisher, never()).publishEvent(any(PlanApprovalCallbackEvent.class));
    }

    @Test
    void shouldConvertGenericCallbackIntoInboundMessage() throws Exception {
        Update update = createCallbackUpdate(CHAT_ID_100, 42, "task:approve:123");

        adapter.consume(update);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageHandler).accept(captor.capture());
        Message message = captor.getValue();

        assertEquals("conv-active", message.getChatId());
        assertEquals("user", message.getRole());
        assertEquals("123", message.getSenderId());
        assertEquals("[Telegram button clicked]\nCallback data: task:approve:123", message.getContent());
        assertEquals(true, message.getMetadata().get("telegram.isInlineCallback"));
        assertEquals("task:approve:123", message.getMetadata().get("telegram.callbackData"));
        assertEquals(CHAT_ID_100, message.getMetadata().get("session.transport.chat.id"));
        verify(telegramClient).execute(any(AnswerCallbackQuery.class));
    }

    private Update createCallbackUpdate(String chatId, int messageId, String data) {
        User from = mock(User.class);
        when(from.getId()).thenReturn(123L);
        org.telegram.telegrambots.meta.api.objects.message.Message message = mock(
                org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(message.getChatId()).thenReturn(Long.parseLong(chatId));
        when(message.getMessageId()).thenReturn(messageId);

        CallbackQuery callback = mock(CallbackQuery.class);
        when(callback.getId()).thenReturn("callback-1");
        when(callback.getFrom()).thenReturn(from);
        when(callback.getMessage()).thenReturn(message);
        when(callback.getData()).thenReturn(data);

        Update update = mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.hasMessage()).thenReturn(false);
        when(update.getCallbackQuery()).thenReturn(callback);

        return update;
    }
}
