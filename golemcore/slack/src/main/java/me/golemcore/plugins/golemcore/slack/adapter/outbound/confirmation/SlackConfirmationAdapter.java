package me.golemcore.plugins.golemcore.slack.adapter.outbound.confirmation;

import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.model.ConfirmationCallbackEvent;
import me.golemcore.plugin.api.extension.port.outbound.ConfirmationPort;
import me.golemcore.plugin.api.runtime.RuntimeConfigService;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.support.SlackActionIds;
import me.golemcore.plugins.golemcore.slack.support.SlackConversationTarget;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import me.golemcore.plugins.golemcore.slack.support.SlackTextSupport;
import me.golemcore.plugins.golemcore.slack.support.SlackTransportChatIds;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlackConfirmationAdapter implements ConfirmationPort {

    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    private final RuntimeConfigService runtimeConfigService;
    private final SlackPluginConfigService configService;
    private final SlackSocketGateway slackSocketGateway;

    @Override
    public boolean isAvailable() {
        SlackPluginConfig config = configService.getConfig();
        return runtimeConfigService.isToolConfirmationEnabled()
                && Boolean.TRUE.equals(config.getEnabled())
                && config.isConfigured()
                && slackSocketGateway.isConnected();
    }

    @Override
    public CompletableFuture<Boolean> requestConfirmation(String chatId, String toolName, String description) {
        if (!isAvailable() || !SlackTransportChatIds.looksLikeSlackTransportChatId(chatId)) {
            return CompletableFuture.completedFuture(true);
        }

        SlackPluginConfig config = configService.getConfig();
        String confirmationId = UUID.randomUUID().toString().substring(0, 8);
        PendingConfirmation pendingConfirmation = new PendingConfirmation();
        pending.put(confirmationId, pendingConfirmation);

        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(chatId);
        slackSocketGateway.postBlocks(
                config.getBotToken(),
                target,
                buildFallbackText(toolName, description),
                buildBlocks(toolName, description, confirmationId))
                .whenComplete((posted, error) -> {
                    if (error != null || posted == null || posted.messageTs() == null) {
                        pending.remove(confirmationId);
                        log.error("[Slack] Failed to send confirmation prompt", error);
                        pendingConfirmation.future.complete(true);
                        return;
                    }
                    pendingConfirmation.bind(posted.channelId(), posted.messageTs());
                });

        int timeoutSeconds = Math.max(1, runtimeConfigService.getToolConfirmationTimeoutSeconds());
        return pendingConfirmation.future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .handle((approved, error) -> {
                    PendingConfirmation removed = pending.remove(confirmationId);
                    if (error != null) {
                        updateStatus(config.getBotToken(), removed, ":hourglass_flowing_sand: Action timed out.");
                        return false;
                    }
                    return approved;
                });
    }

    @EventListener
    public void onConfirmationCallback(ConfirmationCallbackEvent event) {
        PendingConfirmation confirmation = pending.remove(event.confirmationId());
        if (confirmation == null) {
            return;
        }

        confirmation.future.complete(event.approved());
        SlackPluginConfig config = configService.getConfig();
        updateStatus(
                config.getBotToken(),
                confirmation,
                event.approved() ? ":white_check_mark: Action confirmed." : ":x: Action cancelled.");
    }

    private List<LayoutBlock> buildBlocks(String toolName, String description, String confirmationId) {
        return asBlocks(
                section(section -> section.text(markdownText(
                        "*Confirm action*\n*Tool:* `" + SlackTextSupport.escapeMrkdwn(toolName) + "`\n*Action:* "
                                + SlackTextSupport.escapeMrkdwn(description)))),
                actions(actions -> actions.elements(asElements(
                        button(button -> button
                                .actionId(SlackActionIds.CONFIRM_APPROVE)
                                .text(plainText("Confirm"))
                                .style("primary")
                                .value(confirmationId)),
                        button(button -> button
                                .actionId(SlackActionIds.CONFIRM_CANCEL)
                                .text(plainText("Cancel"))
                                .style("danger")
                                .value(confirmationId))))));
    }

    private String buildFallbackText(String toolName, String description) {
        return "Confirm action for " + toolName + ": " + description;
    }

    private void updateStatus(String botToken, PendingConfirmation confirmation, String statusText) {
        if (confirmation == null || confirmation.channelId == null || confirmation.messageTs == null) {
            return;
        }
        slackSocketGateway.updateMessage(
                botToken,
                confirmation.channelId,
                confirmation.messageTs,
                statusText,
                List.of());
    }

    private static final class PendingConfirmation {

        private final CompletableFuture<Boolean> future = new CompletableFuture<>();
        private volatile String channelId;
        private volatile String messageTs;

        private void bind(String channelId, String messageTs) {
            this.channelId = channelId;
            this.messageTs = messageTs;
        }
    }
}
