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
public class PinchTabActionToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_KIND = "kind";
    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_REF = "ref";
    private static final String PARAM_SELECTOR = "selector";
    private static final String PARAM_KEY = "key";
    private static final String PARAM_TEXT = "text";
    private static final String PARAM_VALUE = "value";
    private static final String PARAM_SCROLL_Y = "scroll_y";
    private static final String PARAM_WAIT_NAV = "wait_nav";

    public PinchTabActionToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_action")
                .description("""
                        Execute a PinchTab browser action. Use refs returned from pinchtab_snapshot or pinchtab_find
                        for element-scoped actions. Supported kinds: click, type, press, focus, fill, hover, select,
                        scroll.
                        """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_KIND, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("click", "type", "press", "focus", "fill", "hover", "select",
                                                "scroll"),
                                        "description", "Action kind."),
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional tab id. Recommended with orchestrator flows."),
                                PARAM_REF, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Element ref from snapshot/find."),
                                PARAM_SELECTOR, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional CSS selector for selector-based actions."),
                                PARAM_KEY, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Keyboard key for press."),
                                PARAM_TEXT, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Text payload for type or fill."),
                                PARAM_VALUE, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Value payload for select. Accepted as a legacy alias for fill."),
                                PARAM_SCROLL_Y, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Vertical scroll delta for scroll."),
                                PARAM_WAIT_NAV, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Wait for navigation after the action completes.")),
                        REQUIRED, List.of(PARAM_KIND)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeAction(parameters));
    }

    private ToolResult executeAction(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        String kind = readString(parameters.get(PARAM_KIND));
        if (!hasText(kind)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "kind is required");
        }
        ToolResult validation = validateAction(kind, parameters);
        if (validation != null) {
            return validation;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", kind);
            String tabId = readString(parameters.get(PARAM_TAB_ID));
            if (hasText(tabId)) {
                body.put("tabId", tabId.trim());
            }
            String ref = readString(parameters.get(PARAM_REF));
            if (hasText(ref)) {
                body.put("ref", ref.trim());
            }
            String selector = readString(parameters.get(PARAM_SELECTOR));
            if (hasText(selector)) {
                body.put("selector", selector.trim());
            }
            String key = readString(parameters.get(PARAM_KEY));
            if (hasText(key)) {
                body.put("key", key.trim());
            }
            String text = readString(parameters.get(PARAM_TEXT));
            String value = readString(parameters.get(PARAM_VALUE));
            String fillText = resolveFillText(parameters);
            if (text != null) {
                body.put("text", text);
            }
            if ("fill".equals(kind) && fillText != null) {
                body.put("text", fillText);
            }
            if (!"fill".equals(kind) && value != null) {
                body.put("value", value);
            }
            Integer scrollY = readInteger(parameters.get(PARAM_SCROLL_Y));
            if (scrollY != null) {
                body.put("scrollY", scrollY);
            }
            if (readBoolean(parameters.get(PARAM_WAIT_NAV), false)) {
                body.put("waitNav", true);
            }

            JsonNode root = getClient().postJson("/action", body);
            StringBuilder output = new StringBuilder("PinchTab ");
            output.append(kind).append(" action completed.");
            String details = getClient().prettyPrint(root);
            if (hasText(details)) {
                output.append("\n\n").append(truncate(details));
            }
            return ToolResult.success(output.toString(), getClient().toObject(root));
        } catch (Exception ex) {
            return failureResult("action", ex);
        }
    }

    private ToolResult validateAction(String kind, Map<String, Object> parameters) {
        return switch (kind) {
        case "click", "focus", "hover" -> requireRefOrSelector(kind, parameters);
        case "type" -> require(kind, parameters, PARAM_TEXT, true);
        case "press" -> requirePress(parameters);
        case "fill" -> requireFill(parameters);
        case "select" -> require(kind, parameters, PARAM_VALUE, true);
        case "scroll" -> null;
        default -> ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Unsupported action kind: " + kind);
        };
    }

    private ToolResult requirePress(Map<String, Object> parameters) {
        String key = readString(parameters.get(PARAM_KEY));
        if (!hasText(key)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "press requires key");
        }
        return null;
    }

    private ToolResult requireFill(Map<String, Object> parameters) {
        String fillText = resolveFillText(parameters);
        if (!hasText(fillText)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "fill requires text");
        }
        return requireTarget("fill", parameters, true);
    }

    private ToolResult requireRefOrSelector(String kind, Map<String, Object> parameters) {
        return requireTarget(kind, parameters, true);
    }

    private ToolResult requireTarget(String kind, Map<String, Object> parameters, boolean allowSelector) {
        String ref = readString(parameters.get(PARAM_REF));
        String selector = readString(parameters.get(PARAM_SELECTOR));
        if (hasText(ref) || (allowSelector && hasText(selector))) {
            return null;
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                kind + " requires ref" + (allowSelector ? " or selector" : ""));
    }

    private ToolResult require(String kind, Map<String, Object> parameters, String valueKey, boolean allowSelector) {
        ToolResult targetResult = requireTarget(kind, parameters, allowSelector);
        if (targetResult != null) {
            return targetResult;
        }
        String value = readString(parameters.get(valueKey));
        if (!hasText(value)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    kind + " requires " + valueKey);
        }
        return null;
    }

    private String resolveFillText(Map<String, Object> parameters) {
        String text = readString(parameters.get(PARAM_TEXT));
        if (hasText(text)) {
            return text;
        }
        String legacyValue = readString(parameters.get(PARAM_VALUE));
        return hasText(legacyValue) ? legacyValue : null;
    }
}
