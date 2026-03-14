package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabNavigateToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_URL = "url";
    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_INSTANCE_ID = "instance_id";
    private static final String PARAM_NEW_TAB = "new_tab";
    private static final String PARAM_TIMEOUT_MS = "timeout_ms";
    private static final String PARAM_BLOCK_IMAGES = "block_images";
    private static final String PARAM_BLOCK_ADS = "block_ads";
    private static final String PARAM_WAIT_FOR = "wait_for";
    private static final String PARAM_WAIT_SELECTOR = "wait_selector";

    public PinchTabNavigateToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_navigate")
                .description(
                        """
                                Navigate a PinchTab browser to a URL. If tab_id is omitted and instance_id is provided,
                                the tool opens a fresh tab in that instance. Advanced flags such as wait_for, block_images,
                                and new_tab are only supported on the top-level navigate route. Reuse the returned tabId in
                                follow-up snapshot, text, find, action, and screenshot calls when working through the orchestrator.
                                """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_URL, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Absolute HTTP(S) URL to open."),
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Existing tab id to reuse."),
                                PARAM_INSTANCE_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description",
                                        "Optional instance id when opening a fresh tab in the orchestrator."),
                                PARAM_NEW_TAB, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Force a new tab when using the top-level navigate route."),
                                PARAM_TIMEOUT_MS, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Optional navigation timeout in milliseconds."),
                                PARAM_BLOCK_IMAGES, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Block images during navigation."),
                                PARAM_BLOCK_ADS, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Block ads during navigation."),
                                PARAM_WAIT_FOR, Map.of(
                                        TYPE, TYPE_STRING,
                                        "enum", List.of("none", "dom", "networkidle", "selector"),
                                        "description", "Navigation readiness mode."),
                                PARAM_WAIT_SELECTOR, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "CSS selector required when wait_for=selector.")),
                        REQUIRED, List.of(PARAM_URL)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeNavigate(parameters));
    }

    private ToolResult executeNavigate(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        String url = readString(parameters.get(PARAM_URL));
        if (!isValidHttpUrl(url)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "url must be an absolute http:// or https:// URL");
        }
        String tabId = readString(parameters.get(PARAM_TAB_ID));
        String instanceId = resolveInstanceId(parameters);
        String waitFor = readString(parameters.get(PARAM_WAIT_FOR));
        if (!hasText(waitFor)) {
            waitFor = getConfig().getDefaultWaitFor();
        }
        String waitSelector = readString(parameters.get(PARAM_WAIT_SELECTOR));
        if ("selector".equals(waitFor) && !hasText(waitSelector)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "wait_selector is required when wait_for=selector");
        }
        try {
            if (hasText(instanceId) && !hasText(tabId)) {
                ToolResult unsupportedFlagsResult = rejectUnsupportedInstanceOpenOptions(parameters);
                if (unsupportedFlagsResult != null) {
                    return unsupportedFlagsResult;
                }
                return openTabInInstance(url, instanceId.trim());
            }
            return navigateExistingOrCurrent(parameters, url, tabId, waitFor, waitSelector);
        } catch (Exception ex) {
            return failureResult("navigate", ex);
        }
    }

    private ToolResult openTabInInstance(String url, String instanceId) throws Exception {
        JsonNode root = getClient().postJson("/instances/" + instanceId + "/tabs/open", Map.of("url", url));
        StringBuilder output = new StringBuilder("Opened PinchTab tab ");
        output.append(root.path("tabId").asText("(unknown)"))
                .append(" in instance ")
                .append(instanceId)
                .append('\n')
                .append(root.path("url").asText(url));
        if (hasText(root.path("title").asText())) {
            output.append('\n').append("Title: ").append(root.path("title").asText());
        }
        return ToolResult.success(output.toString(), getClient().toObject(root));
    }

    private ToolResult navigateExistingOrCurrent(
            Map<String, Object> parameters,
            String url,
            String tabId,
            String waitFor,
            String waitSelector) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (hasText(tabId)) {
            body.put("tabId", tabId.trim());
        }
        Integer timeoutMs = readInteger(parameters.get(PARAM_TIMEOUT_MS));
        if (timeoutMs != null && timeoutMs > 0) {
            body.put("timeout", timeoutMs);
        }
        body.put("blockImages", readBoolean(parameters.get(PARAM_BLOCK_IMAGES),
                Boolean.TRUE.equals(getConfig().getDefaultBlockImages())));
        if (parameters.containsKey(PARAM_BLOCK_ADS)) {
            body.put("blockAds", readBoolean(parameters.get(PARAM_BLOCK_ADS), false));
        }
        if (parameters.containsKey(PARAM_NEW_TAB)) {
            body.put("newTab", readBoolean(parameters.get(PARAM_NEW_TAB), false));
        }
        if (hasText(waitFor)) {
            body.put("waitFor", waitFor);
        }
        if (hasText(waitSelector)) {
            body.put("waitSelector", waitSelector.trim());
        }

        JsonNode root = getClient().postJson("/navigate", body);
        StringBuilder output = new StringBuilder("PinchTab navigated");
        if (hasText(root.path("tabId").asText())) {
            output.append(" tab ").append(root.path("tabId").asText());
        }
        output.append(" to ").append(root.path("url").asText(url));
        if (hasText(root.path("title").asText())) {
            output.append('\n').append("Title: ").append(root.path("title").asText());
        }
        output.append("\nTake a snapshot next if you need fresh refs.");
        return ToolResult.success(output.toString(), getClient().toObject(root));
    }

    private boolean usesAdvancedNavigateOptions(Map<String, Object> parameters) {
        return parameters.containsKey(PARAM_NEW_TAB)
                || parameters.containsKey(PARAM_TIMEOUT_MS)
                || parameters.containsKey(PARAM_BLOCK_IMAGES)
                || parameters.containsKey(PARAM_BLOCK_ADS)
                || parameters.containsKey(PARAM_WAIT_FOR)
                || parameters.containsKey(PARAM_WAIT_SELECTOR);
    }

    private ToolResult rejectUnsupportedInstanceOpenOptions(Map<String, Object> parameters) {
        if (!usesAdvancedNavigateOptions(parameters)) {
            return null;
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                "instance_id without tab_id opens a fresh tab and does not support new_tab, timeout_ms, block_images, "
                        + "block_ads, wait_for, or wait_selector. Open the tab first, then reuse tab_id for advanced "
                        + "navigation options.");
    }

    private boolean isValidHttpUrl(String url) {
        if (!hasText(url)) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return uri.getScheme() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
