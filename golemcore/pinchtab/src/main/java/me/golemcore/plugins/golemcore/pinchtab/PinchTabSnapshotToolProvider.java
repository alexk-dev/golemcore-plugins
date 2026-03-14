package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabSnapshotToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_FORMAT = "format";
    private static final String PARAM_DIFF = "diff";
    private static final String PARAM_SELECTOR = "selector";
    private static final String PARAM_MAX_TOKENS = "max_tokens";
    private static final String PARAM_DEPTH = "depth";

    public PinchTabSnapshotToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_snapshot")
                .description(
                        """
                                Get an accessibility snapshot from PinchTab. The compact interactive format is the recommended
                                default for LLM browsing loops because it returns reusable refs such as e5 or e12 with fewer tokens.
                                """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Optional tab id. Strongly recommended when using the orchestrator."),
                                PARAM_FILTER, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("interactive", "all"),
                                        "description", "Snapshot filter."),
                                PARAM_FORMAT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("compact", "text", "json", "yaml"),
                                        "description", "Snapshot response format."),
                                PARAM_DIFF, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Only return changes since the previous snapshot."),
                                PARAM_SELECTOR, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional CSS selector to scope the snapshot."),
                                PARAM_MAX_TOKENS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional PinchTab maxTokens limit."),
                                PARAM_DEPTH, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional DOM depth limit."))))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeSnapshot(parameters));
    }

    private ToolResult executeSnapshot(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        try {
            String filter = readString(parameters.get(PARAM_FILTER));
            if (!hasText(filter)) {
                filter = getConfig().getDefaultSnapshotFilter();
            }
            String format = readString(parameters.get(PARAM_FORMAT));
            if (!hasText(format)) {
                format = getConfig().getDefaultSnapshotFormat();
            }
            Map<String, Object> query = new LinkedHashMap<>();
            String tabId = readString(parameters.get(PARAM_TAB_ID));
            if (hasText(tabId)) {
                query.put("tabId", tabId.trim());
            }
            query.put("filter", filter);
            query.put("format", format);
            if (readBoolean(parameters.get(PARAM_DIFF), false)) {
                query.put("diff", true);
            }
            String selector = readString(parameters.get(PARAM_SELECTOR));
            if (hasText(selector)) {
                query.put("selector", selector.trim());
            }
            Integer maxTokens = readInteger(parameters.get(PARAM_MAX_TOKENS));
            if (maxTokens != null && maxTokens > 0) {
                query.put("maxTokens", maxTokens);
            }
            Integer depth = readInteger(parameters.get(PARAM_DEPTH));
            if (depth != null && depth > 0) {
                query.put("depth", depth);
            }

            String snapshot = getClient().getText("/snapshot", query);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tabId", hasText(tabId) ? tabId.trim() : "");
            data.put("filter", filter);
            data.put("format", format);
            data.put("diff", readBoolean(parameters.get(PARAM_DIFF), false));

            StringBuilder output = new StringBuilder("PinchTab snapshot");
            output.append(" (filter=").append(filter).append(", format=").append(format).append(")\n\n");
            output.append(truncate(snapshot));
            return ToolResult.success(output.toString(), data);
        } catch (Exception ex) {
            return failureResult("snapshot", ex);
        }
    }
}
