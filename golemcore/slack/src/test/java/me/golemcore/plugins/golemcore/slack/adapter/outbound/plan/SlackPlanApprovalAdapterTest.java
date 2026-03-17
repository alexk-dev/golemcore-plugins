package me.golemcore.plugins.golemcore.slack.adapter.outbound.plan;

import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.ButtonElement;
import me.golemcore.plugin.api.extension.model.PlanApprovalCallbackEvent;
import me.golemcore.plugin.api.extension.model.PlanExecutionCompletedEvent;
import me.golemcore.plugin.api.extension.model.PlanReadyEvent;
import me.golemcore.plugin.api.runtime.PlanExecutionService;
import me.golemcore.plugin.api.runtime.PlanService;
import me.golemcore.plugin.api.runtime.model.Plan;
import me.golemcore.plugin.api.runtime.model.PlanStep;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfig;
import me.golemcore.plugins.golemcore.slack.SlackPluginConfigService;
import me.golemcore.plugins.golemcore.slack.support.SlackSocketGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackPlanApprovalAdapterTest {

    private PlanService planService;
    private PlanExecutionService planExecutionService;
    private SlackPluginConfigService configService;
    private SlackSocketGateway slackSocketGateway;
    private SlackPlanApprovalAdapter adapter;
    private Plan plan;

    @BeforeEach
    void setUp() {
        planService = mock(PlanService.class);
        planExecutionService = mock(PlanExecutionService.class);
        configService = mock(SlackPluginConfigService.class);
        slackSocketGateway = mock(SlackSocketGateway.class);
        adapter = new SlackPlanApprovalAdapter(planService, planExecutionService, configService, slackSocketGateway);

        when(planService.isFeatureEnabled()).thenReturn(true);
        when(slackSocketGateway.isConnected()).thenReturn(true);
        when(configService.getConfig()).thenReturn(SlackPluginConfig.builder()
                .enabled(true)
                .botToken("xoxb-test")
                .appToken("xapp-test")
                .build());
        when(slackSocketGateway.postBlocks(anyString(), any(), anyString(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(slackSocketGateway.postMessage(anyString(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(planExecutionService.executePlan(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        plan = Plan.builder()
                .id("plan-1")
                .title("Deploy release")
                .steps(List.of(PlanStep.builder()
                        .toolName("shell")
                        .description("Run deploy script")
                        .build()))
                .modelTier("standard")
                .build();
        when(planService.getPlan("plan-1")).thenReturn(Optional.of(plan));
    }

    @Test
    void shouldSendInteractivePlanApprovalForSlackChats() {
        adapter.onPlanReady(new PlanReadyEvent("plan-1", "C123::1710000000.001"));

        verify(slackSocketGateway).postBlocks(anyString(), any(), anyString(), anyList());
        assertEquals("plan-1", extractApproveValue());
    }

    @Test
    void shouldIgnoreNonSlackPlanReadyEvents() {
        adapter.onPlanReady(new PlanReadyEvent("plan-1", "telegram-42"));

        verify(slackSocketGateway, never()).postBlocks(anyString(), any(), anyString(), anyList());
    }

    @Test
    void shouldApprovePlanFromInteractiveCallback() {
        adapter.onPlanApproval(new PlanApprovalCallbackEvent(
                "plan-1",
                "approve",
                "C123::1710000000.001",
                "1710000000.900"));

        verify(planService).approvePlan("plan-1");
        verify(planExecutionService).executePlan("plan-1");
        verify(slackSocketGateway).updateMessage(
                "xoxb-test",
                "C123",
                "1710000000.900",
                ":white_check_mark: Plan approved. Executing...",
                List.of());
    }

    @Test
    void shouldSendExecutionSummaryBackToSlack() {
        adapter.onPlanExecutionCompleted(new PlanExecutionCompletedEvent(
                "plan-1",
                "D123",
                "Plan finished"));

        verify(slackSocketGateway).postMessage(anyString(), any(), anyString());
    }

    @SuppressWarnings("unchecked")
    private String extractApproveValue() {
        ArgumentCaptor<List<LayoutBlock>> blocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(slackSocketGateway).postBlocks(anyString(), any(), anyString(), blocksCaptor.capture());
        ActionsBlock actionsBlock = (ActionsBlock) blocksCaptor.getValue().get(1);
        ButtonElement approveButton = (ButtonElement) actionsBlock.getElements().get(0);
        return approveButton.getValue();
    }
}
