package me.golemcore.plugins.golemcore.slack.support;

import com.slack.api.Slack;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.request.auth.AuthTestRequest;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.response.auth.AuthTestResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SlackBoltSocketGateway implements SlackSocketGateway {

    private static final String DM_CHANNEL_TYPE = "im";
    private static final String GROUP_DM_CHANNEL_TYPE = "mpim";
    private static final String CHANNEL_TYPE = "channel";
    private static final String PRIVATE_CHANNEL_TYPE = "group";
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@[A-Z0-9]+>");
    private static final Pattern ACTION_ID_PATTERN = Pattern.compile("^golemcore\\.(confirm|plan)\\..+$");

    private final Object lifecycleLock = new Object();

    private volatile SocketModeApp socketModeApp;
    private volatile Thread socketModeThread;
    private volatile boolean connected;

    @Override
    public void connect(
            String appToken,
            String botToken,
            Consumer<SlackInboundEnvelope> inboundHandler,
            Consumer<SlackActionEnvelope> actionHandler) {
        if (inboundHandler == null) {
            throw new IllegalArgumentException("inboundHandler is required");
        }
        if (actionHandler == null) {
            throw new IllegalArgumentException("actionHandler is required");
        }

        synchronized (lifecycleLock) {
            disconnectInternal();

            AppConfig appConfig = AppConfig.builder()
                    .singleTeamBotToken(botToken)
                    .build();
            App app = new App(appConfig);

            app.event(AppMentionEvent.class, (payload, ctx) -> {
                emitAppMention(payload.getEvent(), inboundHandler);
                return ctx.ack();
            });
            app.event(MessageEvent.class, (payload, ctx) -> {
                emitMessageEvent(payload.getEvent(), inboundHandler);
                return ctx.ack();
            });
            app.blockAction(ACTION_ID_PATTERN, (req, ctx) -> {
                emitBlockAction(req.getPayload(), actionHandler);
                return ctx.ack();
            });

            SocketModeApp modeApp;
            try {
                resolveBotUserId(botToken);
                modeApp = new SocketModeApp(appToken, app);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to initialize Slack Socket Mode app", ex);
            }
            Thread thread = new Thread(() -> runSocketMode(modeApp), "slack-socket-mode");
            thread.setDaemon(true);

            socketModeApp = modeApp;
            socketModeThread = thread;
            connected = true;
            thread.start();
        }
    }

    @Override
    public void disconnect() {
        synchronized (lifecycleLock) {
            disconnectInternal();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public CompletableFuture<Void> postMessage(String botToken, SlackConversationTarget target, String text) {
        return postBlocks(botToken, target, text, List.of()).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<SlackPostedMessage> postBlocks(
            String botToken,
            SlackConversationTarget target,
            String text,
            List<LayoutBlock> blocks) {
        return doPostBlocks(botToken, target, text, blocks);
    }

    @Override
    public CompletableFuture<Void> updateMessage(
            String botToken,
            String channelId,
            String messageTs,
            String text,
            List<LayoutBlock> blocks) {
        return CompletableFuture.runAsync(() -> {
            ChatUpdateRequest.ChatUpdateRequestBuilder request = ChatUpdateRequest.builder()
                    .channel(channelId)
                    .ts(messageTs)
                    .text(text);
            if (blocks != null) {
                request.blocks(blocks);
            }

            try {
                ChatUpdateResponse response = Slack.getInstance()
                        .methods(botToken)
                        .chatUpdate(request.build());
                if (!response.isOk()) {
                    throw new IllegalStateException("Slack API error: " + response.getError());
                }
            } catch (IOException | SlackApiException ex) {
                throw new IllegalStateException("Failed to update Slack message", ex);
            }
        });
    }

    private CompletableFuture<SlackPostedMessage> doPostBlocks(
            String botToken,
            SlackConversationTarget target,
            String text,
            List<LayoutBlock> blocks) {
        return CompletableFuture.supplyAsync(() -> {
            ChatPostMessageRequest.ChatPostMessageRequestBuilder request = ChatPostMessageRequest.builder()
                    .channel(target.channelId())
                    .text(text);
            if (blocks != null && !blocks.isEmpty()) {
                request.blocks(blocks);
            }
            if (target.threaded()) {
                request.threadTs(target.threadTs());
            }

            try {
                ChatPostMessageResponse response = Slack.getInstance()
                        .methods(botToken)
                        .chatPostMessage(request.build());
                if (!response.isOk()) {
                    throw new IllegalStateException("Slack API error: " + response.getError());
                }
                return new SlackPostedMessage(
                        target.channelId(),
                        safeTrim(response.getTs()),
                        target.threaded() ? target.threadTs() : null);
            } catch (IOException | SlackApiException ex) {
                throw new IllegalStateException("Failed to send Slack message", ex);
            }
        });
    }

    private void runSocketMode(SocketModeApp modeApp) {
        try {
            modeApp.start();
        } catch (Exception ex) {
            if (connected) {
                log.error("[Slack] Socket Mode stopped unexpectedly", ex);
            }
        } finally {
            synchronized (lifecycleLock) {
                if (Objects.equals(socketModeApp, modeApp)) {
                    connected = false;
                    clearSocketReferences();
                }
            }
        }
    }

    private void disconnectInternal() {
        connected = false;

        SocketModeApp currentApp = socketModeApp;
        Thread currentThread = socketModeThread;
        clearSocketReferences();

        if (currentApp != null) {
            try {
                currentApp.close();
            } catch (Exception ex) {
                log.warn("[Slack] Failed to close Socket Mode app: {}", ex.getMessage());
            }
        }

        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    @SuppressWarnings("PMD.NullAssignment")
    private void clearSocketReferences() {
        socketModeApp = null;
        socketModeThread = null;
    }

    private void emitAppMention(AppMentionEvent event, Consumer<SlackInboundEnvelope> inboundHandler) {
        if (event == null) {
            return;
        }
        String channelId = safeTrim(event.getChannel());
        String userId = safeTrim(event.getUser());
        String eventTs = safeTrim(event.getTs());
        String rootThreadTs = firstNonBlank(event.getThreadTs(), eventTs);
        String text = normalizeMentionText(event.getText());
        if (channelId == null || userId == null || eventTs == null || rootThreadTs == null || text == null) {
            return;
        }
        inboundHandler.accept(new SlackInboundEnvelope(
                channelId,
                CHANNEL_TYPE,
                userId,
                text,
                eventTs,
                rootThreadTs,
                false,
                true));
    }

    private void emitMessageEvent(MessageEvent event, Consumer<SlackInboundEnvelope> inboundHandler) {
        if (event == null || safeTrim(event.getBotId()) != null) {
            return;
        }
        String channelId = safeTrim(event.getChannel());
        String channelType = safeTrim(event.getChannelType());
        String userId = safeTrim(event.getUser());
        String eventTs = safeTrim(event.getTs());
        String text = safeTrim(event.getText());
        if (channelId == null || channelType == null || userId == null || eventTs == null || text == null) {
            return;
        }

        if (DM_CHANNEL_TYPE.equalsIgnoreCase(channelType)) {
            inboundHandler.accept(new SlackInboundEnvelope(
                    channelId,
                    DM_CHANNEL_TYPE,
                    userId,
                    text,
                    eventTs,
                    null,
                    true,
                    false));
            return;
        }

        if (!CHANNEL_TYPE.equalsIgnoreCase(channelType)
                && !PRIVATE_CHANNEL_TYPE.equalsIgnoreCase(channelType)
                && !GROUP_DM_CHANNEL_TYPE.equalsIgnoreCase(channelType)) {
            return;
        }

        String threadTs = safeTrim(event.getThreadTs());
        if (threadTs == null) {
            return;
        }
        String normalizedText = normalizeMessageText(text);
        if (normalizedText == null) {
            return;
        }
        inboundHandler.accept(new SlackInboundEnvelope(
                channelId,
                channelType,
                userId,
                normalizedText,
                eventTs,
                threadTs,
                false,
                false));
    }

    private void emitBlockAction(BlockActionPayload payload, Consumer<SlackActionEnvelope> actionHandler) {
        if (payload == null || payload.getActions() == null || payload.getActions().isEmpty()) {
            return;
        }

        BlockActionPayload.Action action = payload.getActions().getFirst();
        String actionId = safeTrim(action.getActionId());
        String actionValue = safeTrim(action.getValue());
        String channelId = firstNonBlank(
                payload.getChannel() != null ? payload.getChannel().getId() : null,
                payload.getContainer() != null ? payload.getContainer().getChannelId() : null);
        String userId = payload.getUser() != null ? safeTrim(payload.getUser().getId()) : null;
        String messageTs = firstNonBlank(
                payload.getContainer() != null ? payload.getContainer().getMessageTs() : null,
                payload.getMessage() != null ? payload.getMessage().getTs() : null);
        String threadTs = firstNonBlank(
                payload.getContainer() != null ? payload.getContainer().getThreadTs() : null,
                payload.getMessage() != null ? payload.getMessage().getThreadTs() : null);
        if (actionId == null || actionValue == null || channelId == null || userId == null || messageTs == null) {
            return;
        }

        actionHandler.accept(new SlackActionEnvelope(actionId, actionValue, channelId, userId, messageTs, threadTs));
    }

    private String resolveBotUserId(String botToken) {
        try {
            AuthTestResponse response = Slack.getInstance()
                    .methods(botToken)
                    .authTest(AuthTestRequest.builder().build());
            if (!response.isOk()) {
                throw new IllegalStateException("Slack auth.test failed: " + response.getError());
            }
            return safeTrim(response.getUserId());
        } catch (IOException | SlackApiException ex) {
            throw new IllegalStateException("Failed to resolve Slack bot user id", ex);
        }
    }

    private String normalizeMessageText(String text) {
        String normalized = safeTrim(text);
        if (normalized == null) {
            return null;
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeMentionText(String text) {
        String normalized = safeTrim(text);
        if (normalized == null) {
            return null;
        }
        String withoutMentions = USER_MENTION_PATTERN.matcher(normalized).replaceAll(" ");
        String collapsed = withoutMentions.replaceAll("\\s+", " ").trim();
        return collapsed.isBlank() ? null : collapsed;
    }

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = safeTrim(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        return safeTrim(fallback);
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
