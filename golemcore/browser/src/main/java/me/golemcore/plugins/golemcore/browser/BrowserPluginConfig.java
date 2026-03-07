package me.golemcore.plugins.golemcore.browser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserPluginConfig {

    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Boolean headless = true;

    @Builder.Default
    private Integer timeoutMs = 30_000;

    @Builder.Default
    private String userAgent = DEFAULT_USER_AGENT;

    public void normalize() {
        if (enabled == null) {
            enabled = true;
        }
        if (headless == null) {
            headless = true;
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = 30_000;
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = DEFAULT_USER_AGENT;
        }
    }
}
