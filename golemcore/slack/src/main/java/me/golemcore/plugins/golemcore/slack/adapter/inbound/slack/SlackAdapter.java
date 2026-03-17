package me.golemcore.plugins.golemcore.slack.adapter.inbound.slack;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.loop.AgentLoop;
import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.extension.model.ContextAttributes;
import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugin.api.extension.model.ProgressUpdate;
import me.golemcore.plugin.api.extension.model.ProgressUpdateType;
import me.golemcore.plugin.api.extension.port.inbound.ChannelPort;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.extension.port.outbound.SessionPort;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.SlackRestartEvent;
import me.golemcore.plugins.golemcore.slack.support.SlackActionEnvelope;
import me.golemcore.plugins.golemcore.slack.support.SlackActionIds;
import me.golemcore.plugins.golemcore.slack.support.SlackConversationTarget;
import me.golemcore.plugins.golemcore.slack.support.SlackInboundEnvelope;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlackAdapter implements ChannelPort {

    private static final String CHANNEL_TYPE = "slack";
    private static final String METADATA_SLACK_CHANNEL_ID = "slack.channel.id";
    private static final String METADATA_SLACK_CHANNEL_TYPE = "slack.channel.type";
    private static final String METADATA_SLACK_EVENT_TS = "slack.event.ts";
    private static final String METADATA_SLACK_THREAD_TS = "slack.thread.ts";

    private final SlackPluginConfigService configService;
    private final SlackSocketGateway slackSocketGateway;
    private final SessionPort sessionPort;
    private final ApplicationEventPublisher eventPublisher;

    private final Object lifecycleLock = new Object();
    private final Map<String, ProgressStatusMessage> progressMessages = new ConcurrentHashMap<>();
    private volatile Consumer<Message> messageHandler;
    private volatile boolean running;

    @Override
    public String getChannelType() {
        return CHANNEL_TYPE;
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                return;
            }

            SlackPluginConfig config = configService.getConfig();
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                log.info("[Slack] Channel disabled");
                return;
            }
            if (!config.isConfigured()) {
                log.warn("[Slack] Bot token or app token is missing, adapter will not start");
                return;
            }

            slackSocketGateway.connect(
                    config.getAppToken(),
                    config.getBotToken(),
                    this::handleInbound,
                    this::handleAction);
            running = true;
            log.info("[Slack] Adapter started");
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            running = false;
            slackSocketGateway.disconnect();
            log.info("[Slack] Adapter stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running && slackSocketGateway.isConnected();
    }

    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String content) {
        return sendMessage(chatId, content, Map.of());
    }

    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String content, Map<String, Object> hints) {
        if (content == null || content.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        SlackPluginConfig config = configService.getConfig();
        if (!config.isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Slack credentials are not configured"));
        }
        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(chatId);
        return slackSocketGateway.postMessage(config.getBotToken(), target, content);
    }

    @Override
    public CompletableFuture<Void> sendMessage(Message message) {
        if (message == null) {
            return CompletableFuture.completedFuture(null);
        }
        return sendMessage(resolveTransportChatId(message), message.getContent());
    }

    @Override
    public CompletableFuture<Void> sendVoice(String chatId, byte[] voiceData) {
        log.debug("[Slack] Voice delivery is not supported");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendProgressUpdate(String chatId, ProgressUpdate update) {
        if (chatId == null || chatId.isBlank() || update == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (update.type() == ProgressUpdateType.CLEAR) {
            progressMessages.remove(chatId);
            return CompletableFuture.completedFuture(null);
        }

        SlackPluginConfig config = configService.getConfig();
        if (!config.isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Slack credentials are not configured"));
        }

        String rendered = renderProgressUpdate(update);
        ProgressStatusMessage current = progressMessages.get(chatId);
        if (current != null && rendered.equals(current.text())) {
            return CompletableFuture.completedFuture(null);
        }
        if (current != null) {
            return slackSocketGateway.updateMessage(
                    config.getBotToken(),
                    current.channelId(),
                    current.messageTs(),
                    rendered,
                    List.of())
                    .handle((ignored, error) -> {
                        if (error == null) {
                            progressMessages.put(chatId,
                                    new ProgressStatusMessage(current.channelId(), current.messageTs(), rendered));
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        log.debug("[Slack] Failed to update progress message in {}: {}", chatId, error.getMessage());
                        return postProgressMessage(chatId, config.getBotToken(), rendered);
                    })
                    .thenCompose(future -> future);
        }

        return postProgressMessage(chatId, config.getBotToken(), rendered);
    }

    @Override
    public boolean isAuthorized(String senderId) {
        SlackPluginConfig config = configService.getConfig();
        return config.getAllowedUserIds().isEmpty() || config.getAllowedUserIds().contains(senderId);
    }

    @Override
    public void onMessage(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    @Override
    public void showTyping(String chatId) {
        // Slack typing is not exposed by the current SDK flow; leave as no-op.
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @EventListener
    public void onSlackRestart(SlackRestartEvent event) {
        synchronized (lifecycleLock) {
            log.info("[Slack] Restart requested");
            if (running) {
                stop();
            }
            start();
        }
    }

    private void handleInbound(SlackInboundEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        if (!isAuthorized(envelope.userId())) {
            log.warn("[Slack] Ignoring message from unauthorized user {}", envelope.userId());
            return;
        }
        if (!isChannelAllowed(envelope.channelId())) {
            log.warn("[Slack] Ignoring message from unauthorized channel {}", envelope.channelId());
            return;
        }
        if (requiresExistingConversation(envelope) && !hasExistingConversation(envelope)) {
            log.debug("[Slack] Ignoring thread reply without active conversation {}", envelope.rootThreadTs());
            return;
        }

        Message message = buildInboundMessage(envelope);
        Consumer<Message> handler = this.messageHandler;
        if (handler != null) {
            handler.accept(message);
        }
        eventPublisher.publishEvent(new AgentLoop.InboundMessageEvent(message));
    }

    private void handleAction(SlackActionEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        if (!isAuthorized(envelope.userId())) {
            log.warn("[Slack] Ignoring action from unauthorized user {}", envelope.userId());
            return;
        }
        if (!isChannelAllowed(envelope.channelId())) {
            log.warn("[Slack] Ignoring action from unauthorized channel {}", envelope.channelId());
            return;
        }

        String actionId = envelope.actionId();
        if (SlackActionIds.CONFIRM_APPROVE.equals(actionId) || SlackActionIds.CONFIRM_CANCEL.equals(actionId)) {
            eventPublisher.publishEvent(new ConfirmationCallbackEvent(
                    envelope.actionValue(),
                    SlackActionIds.CONFIRM_APPROVE.equals(actionId),
                    envelope.transportChatId(),
                    envelope.messageTs()));
            return;
        }
        if (SlackActionIds.PLAN_APPROVE.equals(actionId) || SlackActionIds.PLAN_CANCEL.equals(actionId)) {
            eventPublisher.publishEvent(new PlanApprovalCallbackEvent(
                    envelope.actionValue(),
                    SlackActionIds.PLAN_APPROVE.equals(actionId) ? "approve" : "cancel",
                    envelope.transportChatId(),
                    envelope.messageTs()));
        }
    }

    private boolean isChannelAllowed(String channelId) {
        SlackPluginConfig config = configService.getConfig();
        return config.getAllowedChannelIds().isEmpty() || config.getAllowedChannelIds().contains(channelId);
    }

    private Message buildInboundMessage(SlackInboundEnvelope envelope) {
        SlackConversationTarget target = resolveConversationTarget(envelope);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ContextAttributes.TRANSPORT_CHAT_ID, target.transportChatId());
        metadata.put(ContextAttributes.CONVERSATION_KEY, target.conversationKey());
        metadata.put(METADATA_SLACK_CHANNEL_ID, envelope.channelId());
        metadata.put(METADATA_SLACK_CHANNEL_TYPE, envelope.channelType());
        metadata.put(METADATA_SLACK_EVENT_TS, envelope.eventTs());
        if (target.threaded()) {
            metadata.put(METADATA_SLACK_THREAD_TS, target.threadTs());
        }

        return Message.builder()
                .id(envelope.eventTs())
                .channelType(CHANNEL_TYPE)
                .chatId(target.conversationKey())
                .senderId(envelope.userId())
                .role("user")
                .content(envelope.text())
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();
    }

    private boolean requiresExistingConversation(SlackInboundEnvelope envelope) {
        return !envelope.directMessage()
                && !envelope.explicitMention()
                && envelope.rootThreadTs() != null
                && !envelope.rootThreadTs().equals(envelope.eventTs());
    }

    private boolean hasExistingConversation(SlackInboundEnvelope envelope) {
        SlackConversationTarget target = resolveConversationTarget(envelope);
        return !sessionPort.listByChannelTypeAndTransportChatId(CHANNEL_TYPE, target.transportChatId()).isEmpty();
    }

    private SlackConversationTarget resolveConversationTarget(SlackInboundEnvelope envelope) {
        SlackPluginConfig config = configService.getConfig();
        return envelope.directMessage() || !Boolean.TRUE.equals(config.getReplyInThread())
                ? SlackConversationTarget.direct(envelope.channelId())
                : SlackConversationTarget.thread(envelope.channelId(), envelope.rootThreadTs());
    }

    private String resolveTransportChatId(Message message) {
        if (message.getMetadata() != null) {
            Object transportChatId = message.getMetadata().get(ContextAttributes.TRANSPORT_CHAT_ID);
            if (transportChatId instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return message.getChatId();
    }

    private CompletableFuture<Void> postProgressMessage(String chatId, String botToken, String rendered) {
        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(chatId);
        return slackSocketGateway.postBlocks(botToken, target, rendered, List.of())
                .handle((posted, error) -> {
                    if (error != null || posted == null || posted.messageTs() == null) {
                        log.warn("[Slack] Failed to send progress update to {}: {}", chatId,
                                error != null ? error.getMessage() : "missing message timestamp");
                        return null;
                    }
                    progressMessages.put(chatId,
                            new ProgressStatusMessage(posted.channelId(), posted.messageTs(), rendered));
                    return null;
                });
    }

    private String renderProgressUpdate(ProgressUpdate update) {
        String prefix = update.type() == ProgressUpdateType.INTENT
                ? "Working on this:\n"
                : "Progress update:\n";
        return prefix + update.text();
    }

    private record ProgressStatusMessage(String channelId, String messageTs, String text) {
    }
}
