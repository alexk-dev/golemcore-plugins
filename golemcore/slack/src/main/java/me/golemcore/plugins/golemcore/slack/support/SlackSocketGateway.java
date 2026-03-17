package me.golemcore.plugins.golemcore.slack.support;

import com.slack.api.model.block.LayoutBlock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface SlackSocketGateway {

    void connect(
            String appToken,
            String botToken,
            Consumer<SlackInboundEnvelope> inboundHandler,
            Consumer<SlackActionEnvelope> actionHandler);

    void disconnect();

    boolean isConnected();

    CompletableFuture<Void> postMessage(String botToken, SlackConversationTarget target, String text);

    CompletableFuture<SlackPostedMessage> postBlocks(
            String botToken,
            SlackConversationTarget target,
            String text,
            List<LayoutBlock> blocks);

    CompletableFuture<Void> updateMessage(
            String botToken,
            String channelId,
            String messageTs,
            String text,
            List<LayoutBlock> blocks);
}
