/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.extension.model.TelegramRestartEvent;
import me.golemcore.plugin.api.extension.loop.AgentLoop;
import me.golemcore.plugin.api.extension.model.AudioFormat;
import me.golemcore.plugin.api.extension.model.ContextAttributes;
import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.extension.model.ProgressUpdate;
import me.golemcore.plugin.api.extension.model.ProgressUpdateType;
import me.golemcore.plugin.api.runtime.UserPreferencesService;
import me.golemcore.plugin.api.runtime.i18n.MessageService;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.telegram.service.TelegramSessionService;
import me.golemcore.plugins.golemcore.telegram.support.StringValueSupport;
import me.golemcore.plugins.golemcore.telegram.support.TelegramMetadataKeys;
import me.golemcore.plugins.golemcore.telegram.support.TelegramTransportSupport;
import me.golemcore.plugin.api.extension.port.inbound.ChannelPort;
import me.golemcore.plugin.api.extension.port.inbound.CommandPort;
import me.golemcore.plugin.api.extension.spi.TelegramWebhookUpdateConsumer;
import me.golemcore.plugin.api.runtime.security.AllowlistValidator;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOrigin;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChannel;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginChat;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginHiddenUser;
import org.telegram.telegrambots.meta.api.objects.messageorigin.MessageOriginUser;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telegram channel adapter using long polling.
 *
 * <p>
 * This adapter implements both {@link ChannelPort} for outbound messaging and
 * {@link LongPollingSingleThreadUpdateConsumer} for inbound updates.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Long polling for incoming messages via Telegram Bot API
 * <li>User authorization via allowlist
 * <li>Command routing (slash commands) before AgentLoop
 * <li>Message splitting for Telegram's 4096 character limit
 * <li>Markdown to HTML formatting via {@link TelegramHtmlFormatter}
 * <li>Voice message download and processing
 * <li>Settings menu with inline keyboard for language selection
 * <li>Tool confirmation callbacks via inline keyboards
 * <li>Photo and document sending
 * </ul>
 *
 * <p>
 * The adapter is always available as a Spring bean but only starts polling if
 * {@code bot.channels.telegram.enabled=true} and a valid token is configured.
 *
 * @see me.golemcore.plugin.api.extension.port.inbound.ChannelPort
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAdapter
        implements ChannelPort, LongPollingSingleThreadUpdateConsumer, TelegramWebhookUpdateConsumer {

    private static final String CHANNEL_TYPE = "telegram";
    private static final String METADATA_ATTACHMENTS = "attachments";
    private static final String ATTACHMENT_TYPE = "type";
    private static final String ATTACHMENT_MIME_TYPE = "mimeType";
    private static final String ATTACHMENT_DATA_BASE64 = "dataBase64";
    private static final String ATTACHMENT_NAME = "name";
    private static final String IMAGE_ATTACHMENT_TYPE = "image";
    private static final String DEFAULT_PHOTO_FILE_NAME = "telegram-photo.jpg";
    private static final String DEFAULT_PHOTO_MIME_TYPE = "image/jpeg";
    private static final String SETTINGS_COMMAND = "settings";
    private static final int CALLBACK_DATA_PARTS_COUNT = 3;
    private static final int TELEGRAM_MAX_MESSAGE_LENGTH = 4096;
    private static final int TELEGRAM_MAX_CAPTION_LENGTH = 1024;
    private static final int INVITE_MAX_FAILED_ATTEMPTS = 3;
    private static final int INVITE_COOLDOWN_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_AFTER_CAP_SECONDS = 30;
    private static final int RETRY_AFTER_DEFAULT_SECONDS = 5;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("retry after (\\d+)");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RuntimeConfigService runtimeConfigService;
    private final AllowlistValidator allowlistValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final TelegramBotsLongPollingApplication botsApplication;
    private final UserPreferencesService preferencesService;
    private final MessageService messageService;
    private final ObjectProvider<CommandPort> commandRouter;
    private final TelegramVoiceHandler voiceHandler;
    private final TelegramMenuHandler menuHandler;
    private final TelegramSessionService telegramSessionService;
    private final TelegramInboundAssembler inboundAssembler;

    private TelegramClient telegramClient;
    private volatile Consumer<Message> messageHandler;
    private volatile boolean running = false;
    private volatile boolean initialized = false;
    private volatile String registeredBotToken = "";
    private final Object lifecycleLock = new Object();
    private final Map<String, InviteAttemptState> inviteAttemptStates = new ConcurrentHashMap<>();

    TelegramAdapter(
            RuntimeConfigService runtimeConfigService,
            AllowlistValidator allowlistValidator,
            ApplicationEventPublisher eventPublisher,
            TelegramBotsLongPollingApplication botsApplication,
            UserPreferencesService preferencesService,
            MessageService messageService,
            ObjectProvider<CommandPort> commandRouter,
            TelegramVoiceHandler voiceHandler,
            TelegramMenuHandler menuHandler,
            TelegramSessionService telegramSessionService) {
        this(
                runtimeConfigService,
                allowlistValidator,
                eventPublisher,
                botsApplication,
                preferencesService,
                messageService,
                commandRouter,
                voiceHandler,
                menuHandler,
                telegramSessionService,
                new TelegramInboundAssembler());
    }

    /**
     * Package-private setter for testing — allows injecting a mock TelegramClient.
     */
    void setTelegramClient(TelegramClient client) {
        this.telegramClient = client;
        this.initialized = true;
    }

    /**
     * Package-private getter for testing.
     */
    TelegramClient getTelegramClient() {
        return this.telegramClient;
    }

    private boolean isEnabled() {
        return runtimeConfigService.isTelegramEnabled();
    }

    private boolean isWebhookMode() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null) {
            return false;
        }
        String transportMode = runtimeConfig.getTelegram().getTransportMode();
        return transportMode != null && "webhook".equalsIgnoreCase(transportMode.trim());
    }

    private synchronized void ensureInitialized() {
        if (initialized || !isEnabled())
            return;

        String token = runtimeConfigService.getTelegramToken();
        if (token == null || token.isBlank()) {
            log.warn("Telegram token not configured, adapter will not start");
            return;
        }
        this.telegramClient = new OkHttpTelegramClient(token);
        initialized = true;
    }

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                log.debug("Telegram adapter already running");
                return;
            }
            if (!isEnabled()) {
                log.info("Telegram channel disabled");
                return;
            }
            ensureInitialized();
            if (telegramClient == null) {
                log.warn("Telegram client not initialized, cannot start");
                return;
            }

            if (isWebhookMode()) {
                registeredBotToken = "";
                running = true;
                log.info("Telegram adapter started in webhook mode");
                return;
            }

            try {
                String token = runtimeConfigService.getTelegramToken();
                botsApplication.registerBot(token, this);
                registeredBotToken = token;
                running = true;
                log.info("Telegram adapter started");
            } catch (TelegramApiException e) {
                if (e.getMessage() != null && e.getMessage().contains("already registered")) {
                    running = true;
                    log.warn("Telegram bot already registered; keeping existing polling session active");
                    return;
                }
                log.error("Failed to start Telegram adapter", e);
            } catch (Exception e) {
                log.error("Failed to start Telegram adapter", e);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            running = false;
            try {
                if (!registeredBotToken.isBlank()) {
                    botsApplication.unregisterBot(registeredBotToken);
                }
                registeredBotToken = "";
                log.info("Telegram adapter stopped");
            } catch (Exception e) {
                log.error("Error stopping Telegram adapter", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (lifecycleLock) {
            stop();
            try {
                botsApplication.close();
            } catch (Exception e) {
                log.error("Error closing Telegram polling application", e);
            }
            inboundAssembler.shutdown();
        }
    }

    /**
     * Handle restart request from dashboard settings UI.
     */
    @EventListener
    @SuppressWarnings("PMD.NullAssignment") // null forces client re-initialization
    public void onTelegramRestart(TelegramRestartEvent event) {
        synchronized (lifecycleLock) {
            log.info("[Telegram] Restart requested from dashboard");
            try {
                if (running) {
                    stop();
                }
                // Reset client to force re-initialization with potentially new token
                this.telegramClient = null;
                this.initialized = false;
                start();
                log.info("[Telegram] Restart completed successfully, running={}", running);
            } catch (Exception e) {
                log.error("[Telegram] Restart failed", e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public CompletableFuture<Void> acceptUpdate(String updateJson) {
        if (!isEnabled() || !isWebhookMode() || StringValueSupport.isBlank(updateJson)) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Update update = OBJECT_MAPPER.readValue(updateJson, Update.class);
                consume(update);
            } catch (Exception e) {
                throw new RuntimeException("Failed to consume Telegram webhook update", e);
            }
        });
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update);
        } else if (update.hasMessage()) {
            handleMessage(update);
        }
    }

    private void handleCallback(Update update) {
        org.telegram.telegrambots.meta.api.objects.CallbackQuery callback = update.getCallbackQuery();
        if (callback.getMessage() == null) {
            log.warn("Callback query without associated message, ignoring");
            return;
        }
        org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage callbackMessage = callback
                .getMessage();
        String chatId = resolveCallbackTransportChatId(callbackMessage);
        String data = callback.getData();
        Integer messageId = callbackMessage.getMessageId();

        log.debug("Callback: {}", data);
        acknowledgeCallback(callback);

        if (data.startsWith("confirm:")) {
            handleConfirmationCallback(chatId, messageId, data);
        } else if (data.startsWith("plan:")) {
            handlePlanCallback(chatId, messageId, data);
        } else if (data.startsWith("menu:")) {
            menuHandler.handleCallback(chatId, messageId, data);
        } else {
            handleGenericCallback(callback, chatId, messageId, data);
        }
    }

    private String resolveCallbackTransportChatId(
            org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage callbackMessage) {
        if (callbackMessage instanceof org.telegram.telegrambots.meta.api.objects.message.Message message) {
            return resolveTransportChatId(message);
        }
        return callbackMessage.getChatId().toString();
    }

    private void acknowledgeCallback(org.telegram.telegrambots.meta.api.objects.CallbackQuery callback) {
        if (callback == null || StringValueSupport.isBlank(callback.getId())) {
            return;
        }
        try {
            executeWithRetry(() -> telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callback.getId())
                    .build()));
        } catch (Exception e) {
            log.debug("Failed to acknowledge Telegram callback {}", callback.getId(), e);
        }
    }

    private void handleGenericCallback(
            org.telegram.telegrambots.meta.api.objects.CallbackQuery callback,
            String chatId,
            Integer messageId,
            String data) {
        User from = callback.getFrom();
        if (from == null || from.getId() == null) {
            return;
        }

        String userId = from.getId().toString();
        if (!isAuthorized(userId)) {
            return;
        }

        String activeConversationKey = telegramSessionService.resolveActiveConversationKey(chatId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ContextAttributes.TRANSPORT_CHAT_ID, chatId);
        metadata.put(ContextAttributes.CONVERSATION_KEY, activeConversationKey);
        metadata.put(TelegramMetadataKeys.RAW_CHAT_ID, TelegramTransportSupport.resolveRawChatId(chatId));
        Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
        if (threadId != null) {
            metadata.put(TelegramMetadataKeys.THREAD_ID, threadId);
        }
        metadata.put("telegram.isInlineCallback", true);
        metadata.put("telegram.callbackQueryId", callback.getId());
        metadata.put("telegram.callbackData", data);
        if (messageId != null) {
            metadata.put("telegram.callbackMessageId", messageId.toString());
        }

        Message message = Message.builder()
                .id(callback.getId())
                .channelType(CHANNEL_TYPE)
                .chatId(activeConversationKey)
                .senderId(userId)
                .role("user")
                .content("[Telegram button clicked]\nCallback data: " + data)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
        emitInboundMessage(message);
    }

    private void handleConfirmationCallback(String chatId, Integer messageId, String data) {
        // Format: confirm:<id>:yes or confirm:<id>:no
        String[] parts = data.split(":");
        if (parts.length != CALLBACK_DATA_PARTS_COUNT) {
            log.warn("Invalid confirmation callback data: {}", data);
            return;
        }

        String confirmationId = parts[1];
        boolean approved = "yes".equals(parts[2]);

        eventPublisher.publishEvent(new ConfirmationCallbackEvent(
                confirmationId, approved, chatId, messageId.toString()));
    }

    private void handlePlanCallback(String chatId, Integer messageId, String data) {
        // Format: plan:<planId>:<action> (approve or cancel)
        String[] parts = data.split(":");
        if (parts.length != CALLBACK_DATA_PARTS_COUNT) {
            log.warn("Invalid plan callback data: {}", data);
            return;
        }

        String planId = parts[1];
        String action = parts[2];

        eventPublisher.publishEvent(new PlanApprovalCallbackEvent(
                planId, action, chatId, messageId.toString()));
    }

    private void handleMessage(Update update) {
        org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage = update.getMessage();
        String chatId = resolveTransportChatId(telegramMessage);
        String userId = telegramMessage.getFrom().getId().toString();

        // Check authorization
        if (!isAuthorized(userId)) {
            List<String> allowedUsers = runtimeConfigService.getTelegramAllowedUsers();
            if (allowedUsers != null && !allowedUsers.isEmpty()) {
                log.warn("Unauthorized user: {} in chat: {} (invited user already registered)", userId, chatId);
                sendMessage(chatId, messageService.getMessage("security.unauthorized"));
                return;
            }

            String text = telegramMessage.hasText() ? telegramMessage.getText().trim() : "";
            if (isInviteCooldownActive(userId)) {
                long secondsLeft = getInviteCooldownSecondsLeft(userId);
                sendMessage(chatId, messageService.getMessage("telegram.invite.cooldown", secondsLeft));
                return;
            }
            if (!text.isEmpty() && runtimeConfigService.redeemInviteCode(text, userId)) {
                clearInviteFailures(userId);
                log.info("Invite code redeemed by user {} in chat {}", userId, chatId);
                sendMessage(chatId, messageService.getMessage("telegram.invite.success"));
                return;
            }
            if (!text.isEmpty()) {
                long secondsLeft = recordInviteFailureAndGetCooldown(userId);
                if (secondsLeft > 0) {
                    sendMessage(chatId, messageService.getMessage("telegram.invite.cooldown", secondsLeft));
                } else {
                    sendMessage(chatId, messageService.getMessage("telegram.invite.invalid"));
                }
            } else {
                sendMessage(chatId, messageService.getMessage("telegram.invite.prompt"));
            }
            return;
        }

        String activeConversationKey = telegramSessionService.resolveActiveConversationKey(chatId);
        String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);

        Message.MessageBuilder messageBuilder = Message.builder()
                .id(telegramMessage.getMessageId().toString())
                .channelType(CHANNEL_TYPE)
                .chatId(activeConversationKey)
                .senderId(userId)
                .role("user")
                .timestamp(Instant.now());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ContextAttributes.TRANSPORT_CHAT_ID, chatId);
        metadata.put(ContextAttributes.CONVERSATION_KEY, activeConversationKey);
        metadata.put(TelegramMetadataKeys.RAW_CHAT_ID, rawChatId);
        metadata.put(TelegramMetadataKeys.CONVERSATION_SCOPE, resolveConversationScope());
        Integer messageThreadId = telegramMessage.getMessageThreadId();
        if (messageThreadId != null) {
            metadata.put(TelegramMetadataKeys.THREAD_ID, messageThreadId);
        }
        if (!StringValueSupport.isBlank(telegramMessage.getMediaGroupId())) {
            metadata.put(TelegramMetadataKeys.MEDIA_GROUP_ID, telegramMessage.getMediaGroupId());
        }
        messageBuilder.metadata(metadata);

        String inboundText = extractInboundText(telegramMessage);

        // Handle text messages
        if (telegramMessage.hasText() && inboundText != null) {
            String text = inboundText;

            // Handle commands
            if (text.startsWith("/")) {
                String[] parts = text.split("\\s+", 2);
                String cmd = parts[0].substring(1).split("@")[0]; // strip / and @botname

                // /menu and /settings open the centralized inline-keyboard menu
                if ("menu".equals(cmd) || SETTINGS_COMMAND.equals(cmd)) {
                    menuHandler.sendMainMenu(chatId);
                    return;
                }

                if ("sessions".equals(cmd)) {
                    menuHandler.sendSessionsMenu(chatId);
                    return;
                }

                if ("new".equals(cmd)) {
                    telegramSessionService.createAndActivateConversation(chatId);
                    sendMessage(chatId, preferencesService.getMessage("command.new.done"));
                    return;
                }

                // Route to CommandRouter
                CommandPort router = commandRouter.getIfAvailable();
                if (router != null && router.hasCommand(cmd)) {
                    List<String> args = parts.length > 1
                            ? Arrays.asList(parts[1].split("\\s+"))
                            : List.of();
                    String sessionId = CHANNEL_TYPE + ":" + activeConversationKey;
                    Map<String, Object> ctx = Map.<String, Object>of(
                            "sessionId", sessionId,
                            "chatId", activeConversationKey,
                            "sessionChatId", activeConversationKey,
                            "transportChatId", chatId,
                            "conversationKey", activeConversationKey,
                            "channelType", CHANNEL_TYPE);
                    try {
                        CommandPort.CommandResult result = router.execute(cmd, args, ctx).join();
                        sendMessage(chatId, result.output());
                    } catch (Exception e) {
                        log.error("Command execution failed: /{}", cmd, e);
                        sendMessage(chatId, "Command failed: " + e.getMessage());
                    }
                    return;
                }
            }
        }

        List<Map<String, Object>> forwardedItems = extractForwardedItems(telegramMessage);
        if (!forwardedItems.isEmpty()) {
            metadata.put(TelegramMetadataKeys.IS_FORWARDED, true);
            metadata.put(TelegramMetadataKeys.FORWARDED_ITEMS, forwardedItems);
        }

        String messageContent = buildInboundContent(inboundText, forwardedItems);
        if (messageContent != null && !messageContent.isBlank()) {
            messageBuilder.content(messageContent);
        }

        // Handle voice messages
        if (telegramMessage.hasVoice()) {
            if (runtimeConfigService.isTelegramTranscribeIncomingEnabled()) {
                processVoiceMessage(telegramMessage, messageBuilder);
            } else {
                messageBuilder.content("[Voice message]");
            }
        }

        attachImageInputs(telegramMessage, metadata);

        Message message = messageBuilder.build();

        if (shouldAggregateMessage(telegramMessage, forwardedItems)) {
            inboundAssembler.submit(
                    new TelegramInboundFragment(buildAggregationKey(chatId, userId, telegramMessage), message),
                    resolveAggregationDelayMs(),
                    this::emitInboundMessage);
            return;
        }

        emitInboundMessage(message);
    }

    private void emitInboundMessage(Message message) {
        Consumer<Message> handler = this.messageHandler;
        if (handler != null) {
            handler.accept(message);
        }
        eventPublisher.publishEvent(new AgentLoop.InboundMessageEvent(message));
    }

    private String resolveTransportChatId(org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage) {
        String rawChatId = telegramMessage.getChatId().toString();
        return TelegramTransportSupport.buildTransportChatId(
                rawChatId,
                telegramMessage.getMessageThreadId(),
                resolveConversationScope());
    }

    private String resolveConversationScope() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null) {
            return "chat";
        }
        String conversationScope = runtimeConfig.getTelegram().getConversationScope();
        if (StringValueSupport.isBlank(conversationScope)) {
            return "chat";
        }
        return conversationScope.trim().toLowerCase(Locale.ROOT);
    }

    private boolean shouldAggregateMessage(
            org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage,
            List<Map<String, Object>> forwardedItems) {
        if (!isAggregationEnabled() || telegramMessage.hasVoice()) {
            return false;
        }
        if (!forwardedItems.isEmpty()) {
            return isForwardedMergeEnabled();
        }
        return isSequentialMergeEnabled();
    }

    private boolean isAggregationEnabled() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null) {
            return true;
        }
        return !Boolean.FALSE.equals(runtimeConfig.getTelegram().getAggregateIncomingMessages());
    }

    private boolean isForwardedMergeEnabled() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null) {
            return true;
        }
        return !Boolean.FALSE.equals(runtimeConfig.getTelegram().getMergeForwardedMessages());
    }

    private boolean isSequentialMergeEnabled() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null) {
            return true;
        }
        return !Boolean.FALSE.equals(runtimeConfig.getTelegram().getMergeSequentialFragments());
    }

    private int resolveAggregationDelayMs() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getTelegram() == null
                || runtimeConfig.getTelegram().getAggregationDelayMs() == null) {
            return 500;
        }
        return Math.max(runtimeConfig.getTelegram().getAggregationDelayMs(), 0);
    }

    private String buildAggregationKey(
            String transportChatId,
            String userId,
            org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage) {
        String mediaGroupId = telegramMessage.getMediaGroupId();
        if (!StringValueSupport.isBlank(mediaGroupId)) {
            return transportChatId + "|user:" + userId + "|media:" + mediaGroupId.trim();
        }
        return transportChatId + "|user:" + userId;
    }

    private boolean isInviteCooldownActive(String userId) {
        InviteAttemptState state = inviteAttemptStates.get(userId);
        return state != null && state.cooldownUntil != null && state.cooldownUntil.isAfter(Instant.now());
    }

    private long getInviteCooldownSecondsLeft(String userId) {
        InviteAttemptState state = inviteAttemptStates.get(userId);
        if (state == null || state.cooldownUntil == null) {
            return 0;
        }
        long seconds = java.time.Duration.between(Instant.now(), state.cooldownUntil).getSeconds();
        return Math.max(seconds, 0);
    }

    private long recordInviteFailureAndGetCooldown(String userId) {
        InviteAttemptState state = inviteAttemptStates.computeIfAbsent(userId, key -> new InviteAttemptState());
        if (state.cooldownUntil != null && state.cooldownUntil.isAfter(Instant.now())) {
            return getInviteCooldownSecondsLeft(userId);
        }

        state.failedAttempts = state.failedAttempts + 1;
        if (state.failedAttempts >= INVITE_MAX_FAILED_ATTEMPTS) {
            state.failedAttempts = 0;
            state.cooldownUntil = Instant.now().plusSeconds(INVITE_COOLDOWN_SECONDS);
            return INVITE_COOLDOWN_SECONDS;
        }
        return 0;
    }

    private void clearInviteFailures(String userId) {
        inviteAttemptStates.remove(userId);
    }

    private static final class InviteAttemptState {
        private int failedAttempts = 0;
        private Instant cooldownUntil;
    }

    private String extractInboundText(org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage) {
        if (telegramMessage.hasText()) {
            return telegramMessage.getText();
        }

        String caption = telegramMessage.getCaption();
        return caption != null && !caption.isBlank() ? caption : null;
    }

    private String buildInboundContent(String inboundText, List<Map<String, Object>> forwardedItems) {
        if (forwardedItems.isEmpty()) {
            return inboundText;
        }

        StringBuilder builder = new StringBuilder("[Forwarded message]\n");
        Map<String, Object> origin = forwardedItems.getFirst();
        appendForwardOriginLine(builder, origin);
        appendForwardSourceLine(builder, origin);
        Object originalDate = origin.get("originalDate");
        if (originalDate instanceof String value && !value.isBlank()) {
            builder.append("Original date: ").append(value).append("\n");
        }
        builder.append("\n");
        if (!StringValueSupport.isBlank(inboundText)) {
            builder.append(inboundText);
        }
        return builder.toString().trim();
    }

    private void appendForwardOriginLine(StringBuilder builder, Map<String, Object> origin) {
        Object displayName = origin.get("fromDisplayName");
        Object username = origin.get("fromUsername");
        if (displayName instanceof String name && !name.isBlank()) {
            builder.append("From: ").append(name);
            if (username instanceof String userName && !userName.isBlank()) {
                builder.append(" (@").append(userName).append(")");
            }
            builder.append("\n");
        }
    }

    private void appendForwardSourceLine(StringBuilder builder, Map<String, Object> origin) {
        Object chatTitle = origin.get("fromChatTitle");
        Object chatUsername = origin.get("fromChatUsername");
        Object originType = origin.get("originType");
        if (!(chatTitle instanceof String title) || title.isBlank()) {
            return;
        }

        String label = "channel".equals(originType) ? "Source: Channel \"" : "Source: Chat \"";
        builder.append(label).append(title).append("\"");
        if (chatUsername instanceof String userName && !userName.isBlank()) {
            builder.append(" (@").append(userName).append(")");
        }
        builder.append("\n");
    }

    private List<Map<String, Object>> extractForwardedItems(
            org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage) {
        MessageOrigin forwardOrigin = telegramMessage.getForwardOrigin();
        if (forwardOrigin == null) {
            return List.of();
        }

        Map<String, Object> forwardedItem = new LinkedHashMap<>();
        forwardedItem.put("originType", normalizeForwardOriginType(forwardOrigin));

        if (forwardOrigin instanceof MessageOriginUser userOrigin) {
            appendForwardUser(forwardedItem, userOrigin.getSenderUser());
            appendForwardDate(forwardedItem, userOrigin.getDate());
        } else if (forwardOrigin instanceof MessageOriginChannel channelOrigin) {
            appendForwardChat(forwardedItem, channelOrigin.getChat());
            appendForwardDate(forwardedItem, channelOrigin.getDate());
            if (channelOrigin.getMessageId() != null) {
                forwardedItem.put("originalMessageId", channelOrigin.getMessageId().toString());
            }
        } else if (forwardOrigin instanceof MessageOriginChat chatOrigin) {
            appendForwardChat(forwardedItem, chatOrigin.getSenderChat());
            appendForwardDate(forwardedItem, chatOrigin.getDate());
        } else if (forwardOrigin instanceof MessageOriginHiddenUser hiddenUserOrigin) {
            String senderName = hiddenUserOrigin.getSenderUserName();
            if (!StringValueSupport.isBlank(senderName)) {
                forwardedItem.put("fromDisplayName", senderName);
            }
            appendForwardDate(forwardedItem, hiddenUserOrigin.getDate());
        }

        if (forwardedItem.get("originalDate") == null && telegramMessage.getForwardDate() != null) {
            forwardedItem.put("originalDate", telegramMessage.getForwardDate().toString());
        }
        return List.of(forwardedItem);
    }

    private String normalizeForwardOriginType(MessageOrigin forwardOrigin) {
        if (forwardOrigin instanceof MessageOriginChannel) {
            return "channel";
        }
        if (forwardOrigin instanceof MessageOriginChat) {
            return "chat";
        }
        if (forwardOrigin instanceof MessageOriginHiddenUser) {
            return "hidden_user";
        }
        return "user";
    }

    private void appendForwardUser(Map<String, Object> forwardedItem, User senderUser) {
        if (senderUser == null) {
            return;
        }
        if (senderUser.getId() != null) {
            forwardedItem.put("fromUserId", senderUser.getId().toString());
        }
        if (!StringValueSupport.isBlank(senderUser.getUserName())) {
            forwardedItem.put("fromUsername", senderUser.getUserName());
        }
        String displayName = buildUserDisplayName(senderUser);
        if (!StringValueSupport.isBlank(displayName)) {
            forwardedItem.put("fromDisplayName", displayName);
        }
    }

    private void appendForwardChat(Map<String, Object> forwardedItem, Chat chat) {
        if (chat == null) {
            return;
        }
        if (chat.getId() != null) {
            forwardedItem.put("fromChatId", chat.getId().toString());
        }
        if (!StringValueSupport.isBlank(chat.getTitle())) {
            forwardedItem.put("fromChatTitle", chat.getTitle());
        }
        if (!StringValueSupport.isBlank(chat.getUserName())) {
            forwardedItem.put("fromChatUsername", chat.getUserName());
        }
    }

    private void appendForwardDate(Map<String, Object> forwardedItem, Integer date) {
        if (date != null) {
            forwardedItem.put("originalDate", date.toString());
        }
    }

    private String buildUserDisplayName(User senderUser) {
        List<String> parts = new ArrayList<>();
        if (!StringValueSupport.isBlank(senderUser.getFirstName())) {
            parts.add(senderUser.getFirstName().trim());
        }
        if (!StringValueSupport.isBlank(senderUser.getLastName())) {
            parts.add(senderUser.getLastName().trim());
        }
        if (!parts.isEmpty()) {
            return String.join(" ", parts);
        }
        return senderUser.getUserName();
    }

    private void attachImageInputs(
            org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage,
            Map<String, Object> metadata) {
        List<Map<String, Object>> attachments = new ArrayList<>();

        if (telegramMessage.hasPhoto() && telegramMessage.getPhoto() != null && !telegramMessage.getPhoto().isEmpty()) {
            appendPhotoAttachment(telegramMessage.getPhoto(), attachments);
        }

        if (telegramMessage.hasDocument() && telegramMessage.getDocument() != null) {
            appendImageDocumentAttachment(telegramMessage.getDocument(), attachments);
        }

        if (!attachments.isEmpty()) {
            metadata.put(METADATA_ATTACHMENTS, attachments);
        }
    }

    private void appendPhotoAttachment(List<PhotoSize> photos, List<Map<String, Object>> attachments) {
        PhotoSize photo = photos.get(photos.size() - 1);
        if (photo == null || photo.getFileId() == null || photo.getFileId().isBlank()) {
            log.warn("Telegram photo update is missing file id, skipping image attachment");
            return;
        }

        try {
            byte[] imageBytes = downloadTelegramFile(photo.getFileId());
            attachments.add(buildImageAttachment(DEFAULT_PHOTO_MIME_TYPE, imageBytes, DEFAULT_PHOTO_FILE_NAME));
        } catch (Exception e) {
            log.warn("Failed to download Telegram photo attachment", e);
        }
    }

    private void appendImageDocumentAttachment(Document document, List<Map<String, Object>> attachments) {
        String mimeType = resolveImageDocumentMimeType(document);
        if (mimeType == null) {
            return;
        }

        String fileId = document.getFileId();
        if (fileId == null || fileId.isBlank()) {
            log.warn("Telegram image document update is missing file id, skipping image attachment");
            return;
        }

        try {
            byte[] imageBytes = downloadTelegramFile(fileId);
            attachments.add(buildImageAttachment(mimeType, imageBytes, resolveDocumentFileName(document, mimeType)));
        } catch (Exception e) {
            log.warn("Failed to download Telegram image document attachment", e);
        }
    }

    private String resolveImageDocumentMimeType(Document document) {
        String mimeType = document.getMimeType();
        if (mimeType != null && mimeType.startsWith("image/")) {
            return mimeType;
        }

        String fileName = document.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        if (normalized.endsWith(".gif")) {
            return "image/gif";
        }
        if (normalized.endsWith(".bmp")) {
            return "image/bmp";
        }
        return null;
    }

    private String resolveDocumentFileName(Document document, String mimeType) {
        String fileName = document.getFileName();
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }

        return switch (mimeType) {
        case "image/png" -> "telegram-image.png";
        case "image/webp" -> "telegram-image.webp";
        case "image/gif" -> "telegram-image.gif";
        case "image/bmp" -> "telegram-image.bmp";
        default -> DEFAULT_PHOTO_FILE_NAME;
        };
    }

    private Map<String, Object> buildImageAttachment(String mimeType, byte[] imageBytes, String fileName) {
        return Map.of(
                ATTACHMENT_TYPE, IMAGE_ATTACHMENT_TYPE,
                ATTACHMENT_MIME_TYPE, mimeType,
                ATTACHMENT_DATA_BASE64, Base64.getEncoder().encodeToString(imageBytes),
                ATTACHMENT_NAME, fileName);
    }

    private void processVoiceMessage(
            org.telegram.telegrambots.meta.api.objects.message.Message telegramMessage,
            Message.MessageBuilder messageBuilder) {
        try {
            org.telegram.telegrambots.meta.api.objects.Voice voice = telegramMessage.getVoice();
            byte[] voiceData = downloadVoice(voice.getFileId());
            int duration = voice.getDuration() != null ? voice.getDuration() : 0;
            log.info("[Voice] Received voice message: {} bytes, {}s duration", voiceData.length, duration);

            messageBuilder
                    .voiceData(voiceData)
                    .audioFormat(AudioFormat.OGG_OPUS);

            String transcription = voiceHandler.handleIncomingVoice(voiceData).get();
            if (transcription != null && !transcription.startsWith("[")) {
                log.info("[Voice] Decoded: \"{}\" ({} chars, {} bytes audio)",
                        truncateForLog(transcription, 100), transcription.length(), voiceData.length);
                messageBuilder.content(transcription).voiceTranscription(transcription);
            } else {
                log.warn("[Voice] Transcription unavailable, using placeholder: {}", transcription);
                messageBuilder.content(transcription != null ? transcription : "[Voice message]");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Voice] Voice processing interrupted", e);
            messageBuilder.content("[Failed to process voice message]");
        } catch (Exception e) {
            log.error("[Voice] Failed to process voice message", e);
            messageBuilder.content("[Failed to process voice message]");
        }
    }

    private byte[] downloadVoice(String fileId) throws TelegramApiException, java.io.IOException {
        return downloadTelegramFile(fileId);
    }

    byte[] downloadTelegramFile(String fileId) throws TelegramApiException, java.io.IOException {
        GetFile getFile = new GetFile(fileId);
        org.telegram.telegrambots.meta.api.objects.File file = executeWithRetry(() -> telegramClient.execute(getFile));

        String filePath = file.getFilePath();
        String token = runtimeConfigService.getTelegramToken();
        String fileUrl = "https://api.telegram.org/file/bot" + token + "/" + filePath;

        try (InputStream is = URI.create(fileUrl).toURL().openStream()) {
            return is.readAllBytes();
        }
    }

    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String content) {
        return sendMessage(chatId, content, Map.of());
    }

    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String content, Map<String, Object> hints) {
        return CompletableFuture.runAsync(() -> {
            try {
                String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
                Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
                ReplyKeyboard replyMarkup = buildInlineKeyboard(hints);
                // Split long messages at paragraph/line boundaries (Telegram limit is 4096
                // chars)
                List<String> chunks = splitAtNewlines(content, 3800);

                for (String chunk : chunks) {
                    String formatted = TelegramHtmlFormatter.format(chunk);

                    // Safety: if formatted exceeds Telegram limit, truncate
                    if (formatted.length() > TELEGRAM_MAX_MESSAGE_LENGTH) {
                        formatted = formatted.substring(0, TELEGRAM_MAX_MESSAGE_LENGTH - 3) + "...";
                    }

                    SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                            .chatId(rawChatId)
                            .text(formatted)
                            .parseMode("HTML");
                    if (threadId != null) {
                        builder.messageThreadId(threadId);
                    }
                    if (replyMarkup != null) {
                        builder.replyMarkup(replyMarkup);
                    }
                    SendMessage sendMessage = builder.build();

                    try {
                        executeWithRetry(() -> telegramClient.execute(sendMessage));
                    } catch (Exception htmlEx) {
                        // Fallback: retry without formatting if HTML parsing fails
                        log.debug("HTML parse failed, retrying as plain text: {}", htmlEx.getMessage());
                        SendMessage.SendMessageBuilder<?, ?> plainBuilder = SendMessage.builder()
                                .chatId(rawChatId)
                                .text(chunk);
                        if (threadId != null) {
                            plainBuilder.messageThreadId(threadId);
                        }
                        if (replyMarkup != null) {
                            plainBuilder.replyMarkup(replyMarkup);
                        }
                        SendMessage plain = plainBuilder.build();
                        executeWithRetry(() -> telegramClient.execute(plain));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to send message to chat: {}", chatId, e);
                throw new RuntimeException("Failed to send message", e);
            }
        });
    }

    /**
     * Split text at paragraph (\n\n) or line (\n) boundaries to keep chunks under
     * maxLength. This prevents splitting in the middle of markdown formatting like
     * **bold** or `code`.
     */
    static List<String> splitAtNewlines(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            if (start + maxLength >= text.length()) {
                chunks.add(text.substring(start));
                break;
            }

            String segment = text.substring(start, start + maxLength);

            // Try paragraph break
            int splitAt = segment.lastIndexOf("\n\n");
            if (splitAt > maxLength / 4) {
                chunks.add(text.substring(start, start + splitAt));
                start += splitAt + 2;
                continue;
            }

            // Try line break
            splitAt = segment.lastIndexOf('\n');
            if (splitAt > maxLength / 4) {
                chunks.add(text.substring(start, start + splitAt));
                start += splitAt + 1;
                continue;
            }

            // Hard split as last resort
            chunks.add(text.substring(start, start + maxLength));
            start += maxLength;
        }

        return chunks;
    }

    @SuppressWarnings({ "unchecked", "PMD.LooseCoupling" })
    private ReplyKeyboard buildInlineKeyboard(Map<String, Object> hints) {
        if (hints == null || hints.isEmpty()) {
            return null;
        }
        Object keyboardValue = hints.get("telegram.inlineKeyboard");
        if (!(keyboardValue instanceof Collection<?> rows) || rows.isEmpty()) {
            return null;
        }

        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (Object rowValue : rows) {
            if (!(rowValue instanceof Collection<?> buttonValues) || buttonValues.isEmpty()) {
                continue;
            }
            InlineKeyboardRow row = new InlineKeyboardRow();
            for (Object buttonValue : buttonValues) {
                if (!(buttonValue instanceof Map<?, ?> rawButton)) {
                    continue;
                }
                Object text = rawButton.get("text");
                Object callbackData = rawButton.get("callbackData");
                if (!(text instanceof String textValue) || textValue.isBlank()
                        || !(callbackData instanceof String callbackValue) || callbackValue.isBlank()) {
                    continue;
                }
                row.add(InlineKeyboardButton.builder()
                        .text(textValue)
                        .callbackData(callbackValue)
                        .build());
            }
            if (!row.isEmpty()) {
                keyboard.add(row);
            }
        }

        if (keyboard.isEmpty()) {
            return null;
        }
        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    private String readTransportChatId(Message message) {
        if (message != null && message.getMetadata() != null) {
            Object transportChatId = message.getMetadata().get(ContextAttributes.TRANSPORT_CHAT_ID);
            if (transportChatId instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return message != null ? message.getChatId() : null;
    }

    @Override
    public CompletableFuture<Void> sendMessage(Message message) {
        String transportChatId = readTransportChatId(message);
        return sendMessage(transportChatId, message.getContent());
    }

    @Override
    public CompletableFuture<Void> sendProgressUpdate(String chatId, ProgressUpdate update) {
        return CompletableFuture.runAsync(() -> {
            if (chatId == null || chatId.isBlank() || update == null) {
                return;
            }
            if (update.type() == ProgressUpdateType.CLEAR) {
                return;
            }

            String rendered = renderProgressUpdate(update);
            sendProgressMessage(chatId, rendered);
        });
    }

    @Override
    public CompletableFuture<Void> sendVoice(String chatId, byte[] voiceData) {
        return CompletableFuture.runAsync(() -> {
            try {
                String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
                Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
                SendVoice.SendVoiceBuilder<?, ?> builder = SendVoice.builder()
                        .chatId(rawChatId)
                        .voice(new InputFile(new ByteArrayInputStream(voiceData), "voice.ogg"));
                if (threadId != null) {
                    builder.messageThreadId(threadId);
                }
                SendVoice sendVoice = builder.build();

                executeWithRetry(() -> telegramClient.execute(sendVoice));
            } catch (Exception e) {
                if (isVoiceForbidden(e)) {
                    log.info("Voice messages forbidden in chat {}, falling back to audio", chatId);
                    sendAudioFallback(chatId, voiceData);
                    return;
                }
                log.error("Failed to send voice to chat: {}", chatId, e);
                throw new RuntimeException("Failed to send voice", e);
            }
        });
    }

    @Override
    public boolean isVoiceResponseEnabled() {
        me.golemcore.plugin.api.runtime.model.RuntimeConfig runtimeConfig = runtimeConfigService.getRuntimeConfig();
        if (runtimeConfig == null || runtimeConfig.getVoice() == null) {
            return false;
        }
        return Boolean.TRUE.equals(runtimeConfig.getVoice().getTelegramRespondWithVoice());
    }

    private void sendAudioFallback(String chatId, byte[] audioData) {
        try {
            String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
            Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
            SendAudio.SendAudioBuilder<?, ?> builder = SendAudio.builder()
                    .chatId(rawChatId)
                    .audio(new InputFile(new ByteArrayInputStream(audioData), "voice.mp3"));
            if (threadId != null) {
                builder.messageThreadId(threadId);
            }
            SendAudio sendAudio = builder.build();
            executeWithRetry(() -> telegramClient.execute(sendAudio));
        } catch (Exception e) {
            log.error("Failed to send audio fallback to chat: {}", chatId, e);
            throw new RuntimeException("Failed to send audio", e);
        }
    }

    private boolean isVoiceForbidden(Exception e) {
        if (e instanceof TelegramApiRequestException reqEx) {
            return reqEx.getApiResponse() != null
                    && reqEx.getApiResponse().contains("VOICE_MESSAGES_FORBIDDEN");
        }
        if (e.getCause() instanceof TelegramApiRequestException reqEx) {
            return reqEx.getApiResponse() != null
                    && reqEx.getApiResponse().contains("VOICE_MESSAGES_FORBIDDEN");
        }
        return false;
    }

    private Integer sendProgressMessage(String chatId, String rendered) {
        try {
            String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
            Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
            SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                    .chatId(rawChatId)
                    .text(TelegramHtmlFormatter.format(rendered))
                    .parseMode("HTML");
            if (threadId != null) {
                builder.messageThreadId(threadId);
            }
            SendMessage sendMessage = builder.build();
            org.telegram.telegrambots.meta.api.objects.message.Message sent = executeWithRetry(
                    () -> telegramClient.execute(sendMessage));
            return sent != null ? sent.getMessageId() : null;
        } catch (Exception e) {
            log.warn("Failed to send progress update to chat {}: {}", chatId, e.getMessage());
            return null;
        }
    }

    private String renderProgressUpdate(ProgressUpdate update) {
        String prefix = update.type() == ProgressUpdateType.INTENT ? "" : "Progress update:\n";
        return prefix + update.text();
    }

    @Override
    public CompletableFuture<Void> sendPhoto(String chatId, byte[] imageData,
            String filename, String caption) {
        return CompletableFuture.runAsync(() -> {
            try {
                String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
                Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
                SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                        .chatId(rawChatId)
                        .photo(new InputFile(new ByteArrayInputStream(imageData), filename));
                if (threadId != null) {
                    builder.messageThreadId(threadId);
                }

                if (caption != null && !caption.isBlank()) {
                    builder.caption(truncateCaption(caption));
                }

                executeWithRetry(() -> telegramClient.execute(builder.build()));
                log.debug("Sent photo '{}' ({} bytes) to chat: {}", filename, imageData.length, chatId);
            } catch (Exception e) {
                log.error("Failed to send photo '{}' to chat: {}", filename, chatId, e);
                throw new RuntimeException("Failed to send photo", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendDocument(String chatId, byte[] fileData,
            String filename, String caption) {
        return CompletableFuture.runAsync(() -> {
            try {
                String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
                Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
                SendDocument.SendDocumentBuilder<?, ?> builder = SendDocument.builder()
                        .chatId(rawChatId)
                        .document(new InputFile(new ByteArrayInputStream(fileData), filename));
                if (threadId != null) {
                    builder.messageThreadId(threadId);
                }

                if (caption != null && !caption.isBlank()) {
                    builder.caption(truncateCaption(caption));
                }

                executeWithRetry(() -> telegramClient.execute(builder.build()));
                log.debug("Sent document '{}' ({} bytes) to chat: {}", filename, fileData.length, chatId);
            } catch (Exception e) {
                log.error("Failed to send document '{}' to chat: {}", filename, chatId, e);
                throw new RuntimeException("Failed to send document", e);
            }
        });
    }

    private String truncateCaption(String caption) {
        if (caption.length() <= TELEGRAM_MAX_CAPTION_LENGTH)
            return caption;
        return caption.substring(0, TELEGRAM_MAX_CAPTION_LENGTH - 3) + "...";
    }

    private static String truncateForLog(String text, int maxLen) {
        if (text == null || text.length() <= maxLen)
            return text;
        return text.substring(0, maxLen) + "...";
    }

    @Override
    public boolean isAuthorized(String senderId) {
        return allowlistValidator.isAllowed(CHANNEL_TYPE, senderId);
    }

    @Override
    public void onMessage(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    @Override
    public void showTyping(String chatId) {
        try {
            String rawChatId = TelegramTransportSupport.resolveRawChatId(chatId);
            Integer threadId = TelegramTransportSupport.resolveThreadId(chatId);
            SendChatAction.SendChatActionBuilder<?, ?> builder = SendChatAction.builder()
                    .chatId(rawChatId)
                    .action(ActionType.TYPING.toString());
            if (threadId != null) {
                builder.messageThreadId(threadId);
            }
            SendChatAction action = builder.build();
            executeWithRetry(() -> telegramClient.execute(action));
        } catch (Exception e) {
            log.debug("Failed to send typing indicator", e);
        }
    }

    // ===== Rate-limit retry logic =====

    @FunctionalInterface
    interface TelegramApiCall<T> {
        T execute() throws TelegramApiException;
    }

    <T> T executeWithRetry(TelegramApiCall<T> call) throws TelegramApiException {
        for (int attempt = 0;; attempt++) {
            try {
                return call.execute();
            } catch (TelegramApiRequestException e) {
                if (!isRateLimited(e) || attempt >= MAX_RETRY_ATTEMPTS) {
                    throw e;
                }
                int retryAfter = extractRetryAfterSeconds(e);
                log.warn("[Telegram] Rate limited (429), waiting {}s before retry (attempt {}/{})",
                        retryAfter, attempt + 1, MAX_RETRY_ATTEMPTS);
                sleepForRetry(retryAfter);
            }
        }
    }

    private boolean isRateLimited(TelegramApiRequestException e) {
        Integer errorCode = e.getErrorCode();
        return errorCode != null && errorCode == HTTP_TOO_MANY_REQUESTS;
    }

    int extractRetryAfterSeconds(TelegramApiRequestException e) {
        if (e.getParameters() != null && e.getParameters().getRetryAfter() != null) {
            return Math.min(e.getParameters().getRetryAfter(), RETRY_AFTER_CAP_SECONDS);
        }
        String message = e.getMessage();
        if (message != null) {
            Matcher matcher = RETRY_AFTER_PATTERN.matcher(message);
            if (matcher.find()) {
                return Math.min(Integer.parseInt(matcher.group(1)), RETRY_AFTER_CAP_SECONDS);
            }
        }
        return RETRY_AFTER_DEFAULT_SECONDS;
    }

    /**
     * Package-private for testing — allows tests to override sleep behavior.
     */
    void sleepForRetry(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
