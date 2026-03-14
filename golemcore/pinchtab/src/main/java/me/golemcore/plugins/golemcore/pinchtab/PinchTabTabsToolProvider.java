package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabTabsToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_INSTANCE_ID = "instance_id";
    private static final String PARAM_TAB_ID = "tab_id";

    public PinchTabTabsToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_tabs")
                .description(
                        """
                                List browser tabs or close a tab. Use operation=list to inspect current tabs and close to close one.
                                Pass instance_id to list tabs for a specific PinchTab orchestrator instance.
                                """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("list", "close"),
                                        "description", "Tab operation."),
                                PARAM_INSTANCE_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional instance id for list."),
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Tab id for close.")),
                        REQUIRED, List.of(PARAM_OPERATION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeTabs(parameters));
    }

    private ToolResult executeTabs(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        String operation = readString(parameters.get(PARAM_OPERATION));
        if (!hasText(operation)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "operation is required");
        }
        try {
            return switch (operation) {
            case "list" -> listTabs(parameters);
            case "close" -> closeTab(parameters);
            default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Unsupported tabs operation: " + operation);
            };
        } catch (Exception ex) {
            return failureResult("tabs operation", ex);
        }
    }

    private ToolResult listTabs(Map<String, Object> parameters) throws Exception {
        String instanceId = resolveInstanceId(parameters);
        JsonNode root = hasText(instanceId)
                ? getClient().getJson("/instances/" + instanceId + "/tabs", Map.of())
                : getClient().getJson("/tabs", Map.of());
        JsonNode tabsNode = root.isArray() ? root : root.path("tabs");
        StringBuilder output = new StringBuilder("PinchTab tabs");
        if (hasText(instanceId)) {
            output.append(" for instance ").append(instanceId);
        }
        if (tabsNode.isArray() && !tabsNode.isEmpty()) {
            output.append(":\n\n");
            for (JsonNode tab : tabsNode) {
                output.append("- ").append(tab.path("id").asText(tab.path("tabId").asText("(unknown)")));
                if (hasText(tab.path("title").asText())) {
                    output.append(" title=").append(tab.path("title").asText());
                }
                if (hasText(tab.path("url").asText())) {
                    output.append("\n  ").append(tab.path("url").asText());
                }
                output.append('\n');
            }
        } else {
            output.append(": no tabs found");
        }
        return ToolResult.success(output.toString(), getClient().toObject(root));
    }

    private ToolResult closeTab(Map<String, Object> parameters) throws Exception {
        String tabId = readString(parameters.get(PARAM_TAB_ID));
        if (!hasText(tabId)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "tab_id is required for close operation");
        }
        JsonNode root = getClient().postJson("/tabs/" + tabId.trim() + "/close", Map.of());
        return ToolResult.success("Closed PinchTab tab " + tabId.trim(), getClient().toObject(root));
    }
}
