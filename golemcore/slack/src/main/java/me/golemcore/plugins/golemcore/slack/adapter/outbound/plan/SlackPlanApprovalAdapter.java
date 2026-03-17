package me.golemcore.plugins.golemcore.slack.adapter.outbound.plan;

import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.extension.model.PlanExecutionCompletedEvent;
import me.golemcore.plugin.api.extension.model.PlanReadyEvent;
import me.golemcore.plugin.api.runtime.PlanExecutionService;
import me.golemcore.plugin.api.runtime.PlanService;
import me.golemcore.plugin.api.runtime.model.Plan;
import me.golemcore.plugin.api.runtime.model.PlanStep;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.support.SlackActionIds;
import me.golemcore.plugins.golemcore.slack.support.SlackConversationTarget;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import me.golemcore.plugins.golemcore.slack.support.SlackTextSupport;
import me.golemcore.plugins.golemcore.slack.support.SlackTransportChatIds;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
public class SlackPlanApprovalAdapter {

    private final PlanService planService;
    private final PlanExecutionService planExecutionService;
    private final SlackPluginConfigService configService;
    private final SlackSocketGateway slackSocketGateway;

    @EventListener
    public void onPlanReady(PlanReadyEvent event) {
        if (!planService.isFeatureEnabled() || !isChannelReady(event.chatId())) {
            return;
        }

        planService.getPlan(event.planId()).ifPresent(plan -> sendPlanForApproval(event.chatId(), plan));
    }

    @EventListener
    public void onPlanApproval(PlanApprovalCallbackEvent event) {
        if (!planService.isFeatureEnabled() || !SlackTransportChatIds.looksLikeSlackTransportChatId(event.chatId())) {
            return;
        }

        SlackPluginConfig config = configService.getConfig();
        if (!config.isConfigured()) {
            return;
        }

        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(event.chatId());
        if ("approve".equals(event.action())) {
            try {
                planService.approvePlan(event.planId());
                slackSocketGateway.updateMessage(
                        config.getBotToken(),
                        target.channelId(),
                        event.messageId(),
                        ":white_check_mark: Plan approved. Executing...",
                        List.of());
                planExecutionService.executePlan(event.planId())
                        .exceptionally(error -> {
                            log.error("[Slack] Failed to execute approved plan {}", event.planId(), error);
                            slackSocketGateway.updateMessage(
                                    config.getBotToken(),
                                    target.channelId(),
                                    event.messageId(),
                                    ":x: Failed to execute approved plan.",
                                    List.of());
                            return null;
                        });
            } catch (Exception error) {
                log.error("[Slack] Failed to approve plan {}", event.planId(), error);
                slackSocketGateway.updateMessage(
                        config.getBotToken(),
                        target.channelId(),
                        event.messageId(),
                        ":x: Failed to approve plan.",
                        List.of());
            }
            return;
        }

        if ("cancel".equals(event.action())) {
            try {
                planService.cancelPlan(event.planId());
                slackSocketGateway.updateMessage(
                        config.getBotToken(),
                        target.channelId(),
                        event.messageId(),
                        ":x: Plan cancelled.",
                        List.of());
            } catch (Exception error) {
                log.error("[Slack] Failed to cancel plan {}", event.planId(), error);
            }
        }
    }

    @EventListener
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {
        if (!isChannelReady(event.chatId())) {
            return;
        }

        SlackPluginConfig config = configService.getConfig();
        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(event.chatId());
        slackSocketGateway.postMessage(config.getBotToken(), target, event.summary())
                .exceptionally(error -> {
                    log.error("[Slack] Failed to deliver plan summary {}", event.planId(), error);
                    return null;
                });
    }

    private void sendPlanForApproval(String chatId, Plan plan) {
        SlackPluginConfig config = configService.getConfig();
        SlackConversationTarget target = SlackConversationTarget.fromTransportChatId(chatId);
        slackSocketGateway.postBlocks(
                config.getBotToken(),
                target,
                buildFallbackText(plan),
                buildBlocks(plan))
                .exceptionally(error -> {
                    log.error("[Slack] Failed to send plan approval for {}", plan.getId(), error);
                    return null;
                });
    }

    private List<LayoutBlock> buildBlocks(Plan plan) {
        return asBlocks(
                section(section -> section.text(markdownText(buildPlanBody(plan)))),
                actions(actions -> actions.elements(asElements(
                        button(button -> button
                                .actionId(SlackActionIds.PLAN_APPROVE)
                                .text(plainText("Approve"))
                                .style("primary")
                                .value(plan.getId())),
                        button(button -> button
                                .actionId(SlackActionIds.PLAN_CANCEL)
                                .text(plainText("Cancel"))
                                .style("danger")
                                .value(plan.getId()))))));
    }

    private String buildPlanBody(Plan plan) {
        List<String> lines = new ArrayList<>();
        lines.add("*Plan ready for approval*");
        if (plan.getTitle() != null && !plan.getTitle().isBlank()) {
            lines.add("*" + SlackTextSupport.escapeMrkdwn(plan.getTitle()) + "*");
        }
        List<PlanStep> steps = plan.getSteps() == null ? List.of() : plan.getSteps();
        for (int index = 0; index < steps.size(); index++) {
            PlanStep step = steps.get(index);
            StringBuilder line = new StringBuilder()
                    .append(index + 1)
                    .append(". `")
                    .append(SlackTextSupport.escapeMrkdwn(step.getToolName()))
                    .append('`');
            if (step.getDescription() != null && !step.getDescription().isBlank()) {
                line.append(" - ")
                        .append(SlackTextSupport.escapeMrkdwn(
                                SlackTextSupport.truncate(step.getDescription(), 80)));
            }
            lines.add(line.toString());
        }
        if (plan.getModelTier() != null && !plan.getModelTier().isBlank()) {
            lines.add("Tier: " + SlackTextSupport.escapeMrkdwn(plan.getModelTier()));
        }
        return String.join("\n", lines);
    }

    private String buildFallbackText(Plan plan) {
        return "Plan ready for approval: " + (plan.getTitle() == null ? plan.getId() : plan.getTitle());
    }

    private boolean isChannelReady(String chatId) {
        SlackPluginConfig config = configService.getConfig();
        return SlackTransportChatIds.looksLikeSlackTransportChatId(chatId)
                && Boolean.TRUE.equals(config.getEnabled())
                && config.isConfigured()
                && slackSocketGateway.isConnected();
    }
}
