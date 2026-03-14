package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabTextToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_MODE = "mode";
    private static final String PARAM_MAX_CHARS = "max_chars";

    public PinchTabTextToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_text")
                .description("""
                        Extract readable text from the current PinchTab page. Use mode=raw for innerText,
                        otherwise readability-style extraction is used by default.
                        """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional tab id."),
                                PARAM_MODE, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("readability", "raw"),
                                        "description", "Text extraction mode."),
                                PARAM_MAX_CHARS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional PinchTab maxChars limit."))))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeText(parameters));
    }

    private ToolResult executeText(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        try {
            String mode = readString(parameters.get(PARAM_MODE));
            if (!hasText(mode)) {
                mode = getConfig().getDefaultTextMode();
            }
            Map<String, Object> query = new LinkedHashMap<>();
            String tabId = readString(parameters.get(PARAM_TAB_ID));
            if (hasText(tabId)) {
                query.put("tabId", tabId.trim());
            }
            if ("raw".equals(mode)) {
                query.put("mode", "raw");
            }
            Integer maxChars = readInteger(parameters.get(PARAM_MAX_CHARS));
            if (maxChars != null && maxChars > 0) {
                query.put("maxChars", maxChars);
            }

            JsonNode root = getClient().getJson("/text", query);
            StringBuilder output = new StringBuilder("PinchTab text");
            if (hasText(root.path("title").asText())) {
                output.append(" for ").append(root.path("title").asText());
            }
            if (hasText(root.path("url").asText())) {
                output.append("\n").append(root.path("url").asText());
            }
            output.append("\n\n").append(truncate(root.path("text").asText("")));
            return ToolResult.success(output.toString(), getClient().toObject(root));
        } catch (Exception ex) {
            return failureResult("text extraction", ex);
        }
    }
}
