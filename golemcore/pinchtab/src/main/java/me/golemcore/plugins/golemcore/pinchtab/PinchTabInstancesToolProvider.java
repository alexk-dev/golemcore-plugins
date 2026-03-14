package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabInstancesToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_INSTANCE_ID = "instance_id";
    private static final String PARAM_PROFILE_ID = "profile_id";
    private static final String PARAM_MODE = "mode";
    private static final String PARAM_PORT = "port";

    public PinchTabInstancesToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_instances")
                .description("""
                        Manage PinchTab orchestrator instances. Use operation=list to inspect browsers,
                        start to launch a new instance, or stop to stop an existing instance.
                        """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_OPERATION, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("list", "start", "stop"),
                                        "description", "Instance operation."),
                                PARAM_INSTANCE_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Instance id for stop."),
                                PARAM_PROFILE_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional profile id when starting an instance."),
                                PARAM_MODE, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("headless", "headed"),
                                        "description", "Launch mode for start."),
                                PARAM_PORT, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional fixed port for start.")),
                        REQUIRED, List.of(PARAM_OPERATION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeInstances(parameters));
    }

    private ToolResult executeInstances(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        String operation = readString(parameters.get(PARAM_OPERATION));
        if (!hasText(operation)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "operation is required");
        }
        try {
            return switch (operation) {
            case "list" -> listInstances();
            case "start" -> startInstance(parameters);
            case "stop" -> stopInstance(parameters);
            default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Unsupported instances operation: " + operation);
            };
        } catch (Exception ex) {
            return failureResult("instances operation", ex);
        }
    }

    private ToolResult listInstances() throws Exception {
        JsonNode root = getClient().getJson("/instances", Map.of());
        StringBuilder output = new StringBuilder("PinchTab instances");
        if (root.isArray() && !root.isEmpty()) {
            output.append(":\n\n");
            for (JsonNode instance : root) {
                output.append("- ").append(instance.path("id").asText("(unknown)"));
                if (hasText(instance.path("profileName").asText())) {
                    output.append(" [").append(instance.path("profileName").asText()).append(']');
                } else if (hasText(instance.path("profileId").asText())) {
                    output.append(" [").append(instance.path("profileId").asText()).append(']');
                }
                if (hasText(instance.path("status").asText())) {
                    output.append(" status=").append(instance.path("status").asText());
                }
                if (instance.has("headless")) {
                    output.append(" mode=").append(instance.path("headless").asBoolean() ? "headless" : "headed");
                }
                if (hasText(instance.path("port").asText())) {
                    output.append(" port=").append(instance.path("port").asText());
                }
                output.append('\n');
            }
        } else {
            output.append(": no running instances");
        }
        return ToolResult.success(output.toString(), getClient().toObject(root));
    }

    private ToolResult startInstance(Map<String, Object> parameters) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        String profileId = readString(parameters.get(PARAM_PROFILE_ID));
        if (hasText(profileId)) {
            body.put("profileId", profileId.trim());
        }
        String mode = readString(parameters.get(PARAM_MODE));
        if (hasText(mode)) {
            body.put("mode", mode.trim());
        }
        Integer port = readInteger(parameters.get(PARAM_PORT));
        if (port != null && port > 0) {
            body.put("port", port.toString());
        }
        JsonNode root = getClient().postJson("/instances/start", body);
        StringBuilder output = new StringBuilder("Started PinchTab instance ");
        output.append(root.path("id").asText("(unknown)"));
        if (hasText(root.path("status").asText())) {
            output.append(" with status=").append(root.path("status").asText());
        }
        if (hasText(root.path("port").asText())) {
            output.append(" on port ").append(root.path("port").asText());
        }
        return ToolResult.success(output.toString(), getClient().toObject(root));
    }

    private ToolResult stopInstance(Map<String, Object> parameters) throws Exception {
        String instanceId = readString(parameters.get(PARAM_INSTANCE_ID));
        if (!hasText(instanceId)) {
            instanceId = getConfig().getDefaultInstanceId();
        }
        if (!hasText(instanceId)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "instance_id is required for stop operation");
        }
        JsonNode root = getClient().postJson("/instances/" + instanceId.trim() + "/stop", Map.of());
        String status = root.path("status").asText("stopped");
        return ToolResult.success("Stopped PinchTab instance " + instanceId.trim() + " (" + status + ")",
                getClient().toObject(root));
    }
}
