package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.extension.model.ContextAttributes;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugin.api.runtime.model.RuntimeConfig;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOrigin;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChannel;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginUser;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramAdapterHandleMessageTest {

    private static final String COMMAND_HELP = "help";

    private TelegramAdapter adapter;
    private TelegramClient telegramClient;
    private AllowlistValidator allowlistValidator;
    private CommandPort commandRouter;
    private UserPreferencesService preferencesService;
    private MessageService messageService;
    private ApplicationEventPublisher eventPublisher;
    private TelegramMenuHandler menuHandler;
    private TelegramSessionService telegramSessionService;
    private RuntimeConfigService runtimeConfigService;
    private RuntimeConfig runtimeConfig;
    private Consumer<Message> messageHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        allowlistValidator = mock(AllowlistValidator.class);
        when(allowlistValidator.isAllowed("telegram", "123")).thenReturn(true);

        eventPublisher = mock(ApplicationEventPublisher.class);
        telegramClient = mock(TelegramClient.class);
        commandRouter = mock(CommandPort.class);
        preferencesService = mock(UserPreferencesService.class);
        messageService = mock(MessageService.class);
        menuHandler = mock(TelegramMenuHandler.class);

        runtimeConfigService = mock(RuntimeConfigService.class);
        runtimeConfig = RuntimeConfig.builder()
                .telegram(RuntimeConfig.TelegramConfig.builder()
                        .enabled(true)
                        .transportMode("polling")
                        .conversationScope("chat")
                        .aggregateIncomingMessages(false)
                        .aggregationDelayMs(25)
                        .mergeForwardedMessages(true)
                        .mergeSequentialFragments(true)
                        .build())
                .build();
        when(runtimeConfigService.isTelegramEnabled()).thenReturn(true);
        when(runtimeConfigService.getTelegramToken()).thenReturn("test-token");
        when(runtimeConfigService.isTelegramTranscribeIncomingEnabled()).thenReturn(false);
        when(runtimeConfigService.getRuntimeConfig()).thenReturn(runtimeConfig);
        telegramSessionService = mock(TelegramSessionService.class);
        when(telegramSessionService.resolveActiveConversationKey(anyString()))
                .thenReturn("conv-active");

        adapter = new TelegramAdapter(
                runtimeConfigService,
                allowlistValidator,
                eventPublisher,
                mock(TelegramBotsLongPollingApplication.class),
                preferencesService,
                messageService,
                new TestObjectProvider<>(commandRouter),
                mock(TelegramVoiceHandler.class),
                menuHandler,
                telegramSessionService,
                new TelegramInboundAssembler());
        adapter.setTelegramClient(telegramClient);

        messageHandler = mock(Consumer.class);
        adapter.onMessage(messageHandler);
    }

    // ===== Command routing =====

    @Test
    void shouldRouteKnownCommandToRouter() throws Exception {
        when(commandRouter.hasCommand(COMMAND_HELP)).thenReturn(true);

        CommandPort.CommandResult result = CommandPort.CommandResult.success("Help text");
        when(commandRouter.execute(eq(COMMAND_HELP), eq(List.of()), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(mock(org.telegram.telegrambots.meta.api.objects.message.Message.class));

        Update update = createTextUpdate(123L, 100L, "/help");
        adapter.consume(update);

        verify(commandRouter).hasCommand(COMMAND_HELP);
        verify(commandRouter).execute(eq(COMMAND_HELP), eq(List.of()), any());
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldRouteCommandWithArgs() throws Exception {
        when(commandRouter.hasCommand("compact")).thenReturn(true);

        CommandPort.CommandResult result = CommandPort.CommandResult.success("Compacted");
        when(commandRouter.execute(eq("compact"), eq(List.of("10")), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(mock(org.telegram.telegrambots.meta.api.objects.message.Message.class));

        Update update = createTextUpdate(123L, 100L, "/compact 10");
        adapter.consume(update);

        verify(commandRouter).execute(eq("compact"), eq(List.of("10")), any());
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldDelegateSettingsToMenuHandler() {
        Update update = createTextUpdate(123L, 100L, "/settings");
        adapter.consume(update);

        verify(menuHandler).sendMainMenu("100");
        verify(commandRouter, never()).hasCommand("settings");
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldDelegateMenuCommandToMenuHandler() {
        Update update = createTextUpdate(123L, 100L, "/menu");
        adapter.consume(update);

        verify(menuHandler).sendMainMenu("100");
        verify(commandRouter, never()).hasCommand("menu");
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldDelegateSessionsCommandToMenuHandler() {
        Update update = createTextUpdate(123L, 100L, "/sessions");
        adapter.consume(update);

        verify(menuHandler).sendSessionsMenu("100");
        verify(commandRouter, never()).hasCommand("sessions");
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldCreateNewTelegramSessionOnNewCommand() throws Exception {
        when(telegramSessionService.createAndActivateConversation("100")).thenReturn("conv-new");
        when(preferencesService.getMessage("command.new.done")).thenReturn("Conversation reset");
        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(mock(org.telegram.telegrambots.meta.api.objects.message.Message.class));

        Update update = createTextUpdate(123L, 100L, "/new");
        adapter.consume(update);

        verify(telegramSessionService).createAndActivateConversation("100");
        verify(telegramClient, timeout(2000)).execute(any(SendMessage.class));
        verify(commandRouter, never()).hasCommand("new");
        verify(messageHandler, never()).accept(any());
    }

    @Test
    void shouldHandleCommandWithBotMention() throws Exception {
        when(commandRouter.hasCommand(COMMAND_HELP)).thenReturn(true);

        CommandPort.CommandResult result = CommandPort.CommandResult.success("Help text");
        when(commandRouter.execute(eq(COMMAND_HELP), eq(List.of()), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(mock(org.telegram.telegrambots.meta.api.objects.message.Message.class));

        // Command with @botname suffix
        Update update = createTextUpdate(123L, 100L, "/help@mybot");
        adapter.consume(update);

        verify(commandRouter).hasCommand(COMMAND_HELP);
    }

    @Test
    void shouldHandleCommandFailure() throws Exception {
        when(commandRouter.hasCommand("failing")).thenReturn(true);
        when(commandRouter.execute(eq("failing"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Command error")));

        when(telegramClient.execute(any(SendMessage.class)))
                .thenReturn(mock(org.telegram.telegrambots.meta.api.objects.message.Message.class));

        Update update = createTextUpdate(123L, 100L, "/failing");
        adapter.consume(update);

        // Should send error message
        verify(telegramClient, timeout(2000)).execute(any(SendMessage.class));
        verify(messageHandler, never()).accept(any());
    }

    // ===== Unknown command passthrough =====

    @Test
    void shouldPassUnknownCommandAsRegularMessage() {
        when(commandRouter.hasCommand("unknown")).thenReturn(false);

        Update update = createTextUpdate(123L, 100L, "/unknown");
        adapter.consume(update);

        // Unknown command should be passed to message handler as text
        verify(messageHandler).accept(any(Message.class));
    }

    // ===== Regular text message =====

    @Test
    void shouldPassRegularTextToHandler() {
        Update update = createTextUpdate(123L, 100L, "Hello world");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        assertEquals("Hello world", msg.getContent());
        assertEquals("conv-active", msg.getChatId());
        assertEquals("123", msg.getSenderId());
        assertEquals("user", msg.getRole());
        assertEquals("telegram", msg.getChannelType());
        assertEquals("100", msg.getMetadata().get(ContextAttributes.TRANSPORT_CHAT_ID));
        assertEquals("conv-active", msg.getMetadata().get(ContextAttributes.CONVERSATION_KEY));
    }

    @Test
    void shouldUseScopedTransportChatIdForThreadScopedConversation() {
        runtimeConfig.getTelegram().setConversationScope("thread");
        when(telegramSessionService.resolveActiveConversationKey("100#thread:55"))
                .thenReturn("conv-thread");

        Update update = createTextUpdate(123L, 100L, 1, 55, "Topic hello", null);
        adapter.consume(update);

        verify(telegramSessionService).resolveActiveConversationKey("100#thread:55");
        Message msg = captureInboundMessage();
        assertEquals("conv-thread", msg.getChatId());
        assertEquals("100#thread:55", msg.getMetadata().get(ContextAttributes.TRANSPORT_CHAT_ID));
        assertEquals("100", msg.getMetadata().get("telegram.rawChatId"));
        assertEquals(55, msg.getMetadata().get("telegram.threadId"));
    }

    @Test
    void shouldAggregateSequentialMessagesIntoSingleInboundMessage() {
        runtimeConfig.getTelegram().setAggregateIncomingMessages(true);
        runtimeConfig.getTelegram().setAggregationDelayMs(40);

        adapter.consume(createTextUpdate(123L, 100L, 1, null, "First chunk", null));
        adapter.consume(createTextUpdate(123L, 100L, 2, null, "Second chunk", null));

        Message msg = captureInboundMessageWithTimeout();
        assertEquals("First chunk\n\nSecond chunk", msg.getContent());
        assertEquals(true, msg.getMetadata().get("telegram.aggregated"));
        assertEquals(2, msg.getMetadata().get("telegram.aggregatedCount"));
        assertEquals(List.of("1", "2"), msg.getMetadata().get("telegram.messageIds"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeForwardMetadataForForwardedUserMessage() {
        User originUser = createUser(777L, "Alice", "alice");
        MessageOriginUser forwardOrigin = new MessageOriginUser(MessageOrigin.USER_TYPE, 1_711_800_000, originUser);

        Update update = createTextUpdate(123L, 100L, 7, null, "Forwarded body", forwardOrigin);
        adapter.consume(update);

        Message msg = captureInboundMessage();
        assertEquals(true, msg.getMetadata().get("telegram.isForwarded"));
        List<Map<String, Object>> forwardedItems = (List<Map<String, Object>>) msg.getMetadata()
                .get("telegram.forwardedItems");
        assertEquals(1, forwardedItems.size());
        assertEquals("user", forwardedItems.get(0).get("originType"));
        assertEquals("777", forwardedItems.get(0).get("fromUserId"));
        assertEquals("alice", forwardedItems.get(0).get("fromUsername"));
        assertEquals("Alice", forwardedItems.get(0).get("fromDisplayName"));
        assertEquals("1711800000", forwardedItems.get(0).get("originalDate"));
        assertEquals(true, msg.getContent().contains("[Forwarded message]"));
        assertEquals(true, msg.getContent().contains("From: Alice (@alice)"));
        assertEquals(true, msg.getContent().contains("Forwarded body"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeChannelOriginForForwardedChannelMessage() {
        Chat originChat = mock(Chat.class);
        when(originChat.getId()).thenReturn(-100L);
        when(originChat.getTitle()).thenReturn("Build Logs");
        when(originChat.getUserName()).thenReturn("build_logs");
        MessageOriginChannel forwardOrigin = new MessageOriginChannel(
                MessageOrigin.CHANNEL_TYPE,
                1_711_800_001,
                originChat,
                88,
                null);

        Update update = createTextUpdate(123L, 100L, 8, null, "Channel body", forwardOrigin);
        adapter.consume(update);

        Message msg = captureInboundMessage();
        List<Map<String, Object>> forwardedItems = (List<Map<String, Object>>) msg.getMetadata()
                .get("telegram.forwardedItems");
        assertEquals("channel", forwardedItems.get(0).get("originType"));
        assertEquals("-100", forwardedItems.get(0).get("fromChatId"));
        assertEquals("Build Logs", forwardedItems.get(0).get("fromChatTitle"));
        assertEquals("build_logs", forwardedItems.get(0).get("fromChatUsername"));
        assertEquals("88", forwardedItems.get(0).get("originalMessageId"));
        assertEquals(true, msg.getContent().contains("Source: Channel \"Build Logs\" (@build_logs)"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStillProcessPhotoOnlyMessageWithoutCaption() throws Exception {
        adapter = spy(adapter);
        adapter.onMessage(messageHandler);
        doReturn(new byte[] { 1, 2, 3 }).when(adapter).downloadTelegramFile("photo-file-id");

        Update update = createPhotoUpdate(123L, 100L, null, "photo-file-id");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        assertNull(msg.getContent());
        Object attachmentsRaw = msg.getMetadata().get("attachments");
        assertNotNull(attachmentsRaw);
        assertEquals(1, ((List<Map<String, Object>>) attachmentsRaw).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAttachPhotoAsImageAttachment() throws Exception {
        adapter = spy(adapter);
        adapter.onMessage(messageHandler);
        doReturn(new byte[] { 1, 2, 3 }).when(adapter).downloadTelegramFile("photo-file-id");

        Update update = createPhotoUpdate(123L, 100L, null, "photo-file-id");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        Object attachmentsRaw = msg.getMetadata().get("attachments");
        assertNotNull(attachmentsRaw);
        Map<String, Object> attachment = ((List<Map<String, Object>>) attachmentsRaw).get(0);
        assertEquals("image", attachment.get("type"));
        assertEquals("image/jpeg", attachment.get("mimeType"));
        assertEquals("telegram-photo.jpg", attachment.get("name"));
        assertEquals(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }), attachment.get("dataBase64"));
    }

    @Test
    void shouldUseCaptionAsContentForPhotoMessage() throws Exception {
        adapter = spy(adapter);
        adapter.onMessage(messageHandler);
        doReturn(new byte[] { 9, 8, 7 }).when(adapter).downloadTelegramFile("photo-file-id");

        Update update = createPhotoUpdate(123L, 100L, "Describe this image", "photo-file-id");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        assertEquals("Describe this image", msg.getContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAttachImageDocumentAsImageAttachment() throws Exception {
        adapter = spy(adapter);
        adapter.onMessage(messageHandler);
        doReturn(new byte[] { 4, 5, 6 }).when(adapter).downloadTelegramFile("document-file-id");

        Update update = createImageDocumentUpdate(123L, 100L, "diagram.png", "image/png", "document-file-id");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        Object attachmentsRaw = msg.getMetadata().get("attachments");
        assertNotNull(attachmentsRaw);
        Map<String, Object> attachment = ((List<Map<String, Object>>) attachmentsRaw).get(0);
        assertEquals("image", attachment.get("type"));
        assertEquals("image/png", attachment.get("mimeType"));
        assertEquals("diagram.png", attachment.get("name"));
        assertEquals(Base64.getEncoder().encodeToString(new byte[] { 4, 5, 6 }), attachment.get("dataBase64"));
    }

    @Test
    void shouldNotOverrideModelTierForImageAttachment() throws Exception {
        adapter = spy(adapter);
        adapter.onMessage(messageHandler);
        doReturn(new byte[] { 1, 1, 1 }).when(adapter).downloadTelegramFile("photo-file-id");

        Update update = createPhotoUpdate(123L, 100L, null, "photo-file-id");
        adapter.consume(update);

        Message msg = captureInboundMessage();
        assertNull(msg.getMetadata().get("modelTier"));
    }

    // ===== Event publishing =====

    @Test
    void shouldPublishInboundMessageEvent() {
        Update update = createTextUpdate(123L, 100L, "Hello");
        adapter.consume(update);

        verify(eventPublisher).publishEvent(any(Object.class));
    }

    // ===== Update without message =====

    @Test
    void shouldIgnoreUpdateWithoutMessageAndWithoutCallback() {
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);
        when(update.hasCallbackQuery()).thenReturn(false);

        adapter.consume(update);

        verify(messageHandler, never()).accept(any());
    }

    // ===== Message without text and without voice =====

    @Test
    void shouldIgnoreMessageWithoutTextVoiceOrSupportedAttachments() {
        User user = createUser(123L);
        org.telegram.telegrambots.meta.api.objects.message.Message telegramMsg = mock(
                org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(telegramMsg.getChatId()).thenReturn(100L);
        when(telegramMsg.getFrom()).thenReturn(user);
        when(telegramMsg.getMessageId()).thenReturn(1);
        when(telegramMsg.hasText()).thenReturn(false);
        when(telegramMsg.hasVoice()).thenReturn(false);
        when(telegramMsg.hasPhoto()).thenReturn(false);
        when(telegramMsg.hasDocument()).thenReturn(false);
        when(telegramMsg.getForwardOrigin()).thenReturn(null);
        when(telegramMsg.getCaption()).thenReturn(null);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(telegramMsg);

        adapter.consume(update);

        verify(messageHandler, never()).accept(any(Message.class));
    }

    // ===== Helpers =====

    private Message captureInboundMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageHandler).accept(captor.capture());
        return captor.getValue();
    }

    private Message captureInboundMessageWithTimeout() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageHandler, timeout(2000)).accept(captor.capture());
        return captor.getValue();
    }

    private Update createTextUpdate(long userId, long chatId, String text) {
        return createTextUpdate(userId, chatId, 1, null, text, null);
    }

    private Update createTextUpdate(long userId, long chatId, int messageId, Integer messageThreadId, String text,
            MessageOrigin forwardOrigin) {
        User user = createUser(userId);
        org.telegram.telegrambots.meta.api.objects.message.Message telegramMsg = mock(
                org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(telegramMsg.getChatId()).thenReturn(chatId);
        when(telegramMsg.getFrom()).thenReturn(user);
        when(telegramMsg.getMessageId()).thenReturn(messageId);
        when(telegramMsg.hasText()).thenReturn(true);
        when(telegramMsg.getText()).thenReturn(text);
        when(telegramMsg.hasVoice()).thenReturn(false);
        when(telegramMsg.getMessageThreadId()).thenReturn(messageThreadId);
        when(telegramMsg.isTopicMessage()).thenReturn(messageThreadId != null);
        when(telegramMsg.getMediaGroupId()).thenReturn(null);
        when(telegramMsg.getForwardOrigin()).thenReturn(forwardOrigin);
        when(telegramMsg.getForwardDate()).thenReturn(forwardOrigin == null ? null : 1_711_800_000);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(telegramMsg);

        return update;
    }

    private Update createPhotoUpdate(long userId, long chatId, String caption, String fileId) {
        User user = createUser(userId);
        PhotoSize photo = mock(PhotoSize.class);
        when(photo.getFileId()).thenReturn(fileId);

        org.telegram.telegrambots.meta.api.objects.message.Message telegramMsg = mock(
                org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(telegramMsg.getChatId()).thenReturn(chatId);
        when(telegramMsg.getFrom()).thenReturn(user);
        when(telegramMsg.getMessageId()).thenReturn(1);
        when(telegramMsg.hasText()).thenReturn(false);
        when(telegramMsg.getCaption()).thenReturn(caption);
        when(telegramMsg.hasVoice()).thenReturn(false);
        when(telegramMsg.hasPhoto()).thenReturn(true);
        when(telegramMsg.getPhoto()).thenReturn(List.of(photo));
        when(telegramMsg.hasDocument()).thenReturn(false);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(telegramMsg);
        return update;
    }

    private Update createImageDocumentUpdate(long userId, long chatId, String fileName, String mimeType,
            String fileId) {
        User user = createUser(userId);
        Document document = mock(Document.class);
        when(document.getFileId()).thenReturn(fileId);
        when(document.getMimeType()).thenReturn(mimeType);
        when(document.getFileName()).thenReturn(fileName);

        org.telegram.telegrambots.meta.api.objects.message.Message telegramMsg = mock(
                org.telegram.telegrambots.meta.api.objects.message.Message.class);
        when(telegramMsg.getChatId()).thenReturn(chatId);
        when(telegramMsg.getFrom()).thenReturn(user);
        when(telegramMsg.getMessageId()).thenReturn(1);
        when(telegramMsg.hasText()).thenReturn(false);
        when(telegramMsg.getCaption()).thenReturn(null);
        when(telegramMsg.hasVoice()).thenReturn(false);
        when(telegramMsg.hasPhoto()).thenReturn(false);
        when(telegramMsg.hasDocument()).thenReturn(true);
        when(telegramMsg.getDocument()).thenReturn(document);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(telegramMsg);
        return update;
    }

    private User createUser(long userId) {
        return createUser(userId, null, null);
    }

    private User createUser(long userId, String firstName, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getFirstName()).thenReturn(firstName);
        when(user.getUserName()).thenReturn(username);
        return user;
    }
}
