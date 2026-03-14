package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabHealthToolProvider extends AbstractPinchTabToolProvider {

    public PinchTabHealthToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_health")
                .description("Check whether the configured PinchTab server is reachable and healthy.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of()))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(this::executeHealthCheck);
    }

    private ToolResult executeHealthCheck() {
        if (!isEnabled()) {
            return disabledResult();
        }
        try {
            Object data = getClient().toObject(getClient().getJson("/health", Map.of()));
            return ToolResult.success("PinchTab health check succeeded.", data);
        } catch (Exception ex) {
            return failureResult("health check", ex);
        }
    }
}
