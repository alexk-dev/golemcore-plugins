package me.golemcore.plugins.golemcore.browser;

import me.golemcore.plugin.api.extension.model.Attachment;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import me.golemcore.plugins.golemcore.browser.adapter.outbound.PlaywrightBrowserClient;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BrowserToolProvider implements ToolProvider {

    private static final String PARAM_URL = "url";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_OBJECT = "object";
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final long TIMEOUT_SECONDS = 30;

    private final PlaywrightBrowserClient browserClient;
    private final BrowserPluginConfigService configService;

    public BrowserToolProvider(PlaywrightBrowserClient browserClient, BrowserPluginConfigService configService) {
        this.browserClient = browserClient;
        this.configService = configService;
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("browse")
                .description("Browse a web page and extract text, HTML, or a screenshot.")
                .inputSchema(Map.of(
                        "type", TYPE_OBJECT,
                        "properties", Map.of(
                                PARAM_URL, Map.of(
                                        "type", TYPE_STRING,
                                        "description", "The URL to browse"),
                                "mode", Map.of(
                                        "type", TYPE_STRING,
                                        "description", "What to extract: 'text' (default), 'html', or 'screenshot'",
                                        "enum", java.util.List.of("text", "html", "screenshot"))),
                        "required", java.util.List.of(PARAM_URL)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            String url = parameters.get(PARAM_URL) instanceof String value ? value : null;
            if (url == null || url.isBlank()) {
                return ToolResult.failure("URL is required");
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (url.contains("://") || url.startsWith("javascript:") || url.startsWith("data:")
                        || url.startsWith("file:")) {
                    return ToolResult.failure("Only http and https URLs are allowed");
                }
                url = "https://" + url;
            }

            String mode = parameters.get("mode") instanceof String value ? value : "text";

            try {
                return switch (mode) {
                case "html" -> executeHtml(url);
                case "screenshot" -> executeScreenshot(url);
                default -> executeText(url);
                };
            } catch (Exception e) {
                return ToolResult.failure("Failed to browse page: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(configService.getConfig().getEnabled());
    }

    private ToolResult executeText(String url) throws Exception {
        PlaywrightBrowserClient.BrowserPageData page = browserClient.navigate(url)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        String text = page.text();
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + "\n... (truncated)";
        }

        String output = String.format("**%s**%n%nURL: %s%n%n%s", page.title(), page.url(), text);
        return ToolResult.success(output, Map.of("title", page.title(), PARAM_URL, page.url()));
    }

    private ToolResult executeHtml(String url) throws Exception {
        String html = browserClient.getHtml(url).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (html != null && html.length() > MAX_TEXT_LENGTH * 2) {
            html = html.substring(0, MAX_TEXT_LENGTH * 2) + "\n... (truncated)";
        }
        return ToolResult.success(html);
    }

    private ToolResult executeScreenshot(String url) throws Exception {
        byte[] screenshot = browserClient.screenshot(url).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String base64 = Base64.getEncoder().encodeToString(screenshot);
        Attachment attachment = Attachment.builder()
                .type(Attachment.Type.IMAGE)
                .data(screenshot)
                .filename("screenshot.png")
                .mimeType("image/png")
                .caption("Screenshot of " + url)
                .build();
        return ToolResult.success(
                "Screenshot captured (" + screenshot.length + " bytes)",
                Map.of("attachment", attachment, "screenshot_base64", base64, "format", "png"));
    }
}
