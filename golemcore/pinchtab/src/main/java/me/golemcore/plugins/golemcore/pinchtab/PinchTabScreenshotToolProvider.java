package me.golemcore.plugins.golemcore.pinchtab;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabScreenshotToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_QUALITY = "quality";

    public PinchTabScreenshotToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_screenshot")
                .description("Capture a screenshot from PinchTab and return it as an image attachment.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional tab id."),
                                PARAM_QUALITY, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "JPEG quality from 1 to 100."))))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeScreenshot(parameters));
    }

    private ToolResult executeScreenshot(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        try {
            Map<String, Object> query = new LinkedHashMap<>();
            String tabId = readString(parameters.get(PARAM_TAB_ID));
            if (hasText(tabId)) {
                query.put("tabId", tabId.trim());
            }
            int quality = readInteger(parameters.get(PARAM_QUALITY),
                    getConfig().getDefaultScreenshotQuality(),
                    1,
                    100);
            query.put("quality", quality);
            query.put("raw", true);

            byte[] bytes = getClient().getBytes("/screenshot", query);
            Attachment attachment = Attachment.builder()
                    .type(Attachment.Type.IMAGE)
                    .data(bytes)
                    .filename("pinchtab-screenshot.jpg")
                    .mimeType("image/jpeg")
                    .caption("PinchTab screenshot")
                    .build();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tabId", hasText(tabId) ? tabId.trim() : "");
            data.put("quality", quality);
            data.put("sizeBytes", bytes.length);
            data.put("attachment", attachment);

            StringBuilder output = new StringBuilder("Captured PinchTab screenshot");
            if (hasText(tabId)) {
                output.append(" for tab ").append(tabId.trim());
            }
            output.append("\nQuality: ").append(quality);
            output.append("\nSize: ").append(bytes.length).append(" bytes");
            return ToolResult.success(output.toString(), data);
        } catch (Exception ex) {
            return failureResult("screenshot", ex);
        }
    }
}
