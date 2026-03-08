package me.golemcore.plugins.golemcore.browserless;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrowserlessPluginConfig {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("markdown", "html", "links", "pdf", "screenshot");
    private static final Set<String> SUPPORTED_WAIT_UNTIL = Set.of(
            "load",
            "domcontentloaded",
            "networkidle0",
            "networkidle2");
    private static final String DEFAULT_BASE_URL = "https://production-sfo.browserless.io";
    private static final String DEFAULT_FORMAT = "markdown";
    private static final String DEFAULT_GOTO_WAIT_UNTIL = "networkidle2";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 300_000;

    @Builder.Default
    private Boolean enabled = false;

    private String apiKey;

    @Builder.Default
    private String baseUrl = DEFAULT_BASE_URL;

    @Builder.Default
    private String defaultFormat = DEFAULT_FORMAT;

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

    @Builder.Default
    private Boolean bestAttempt = false;

    @Builder.Default
    private String gotoWaitUntil = DEFAULT_GOTO_WAIT_UNTIL;

    @Builder.Default
    private Integer gotoTimeoutMs = DEFAULT_TIMEOUT_MS;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        } else {
            baseUrl = trimTrailingSlash(baseUrl.trim());
        }
        if (defaultFormat == null || !SUPPORTED_FORMATS.contains(defaultFormat)) {
            defaultFormat = DEFAULT_FORMAT;
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        if (timeoutMs > MAX_TIMEOUT_MS) {
            timeoutMs = MAX_TIMEOUT_MS;
        }
        if (bestAttempt == null) {
            bestAttempt = false;
        }
        if (gotoWaitUntil == null || !SUPPORTED_WAIT_UNTIL.contains(gotoWaitUntil)) {
            gotoWaitUntil = DEFAULT_GOTO_WAIT_UNTIL;
        }
        if (gotoTimeoutMs == null || gotoTimeoutMs <= 0) {
            gotoTimeoutMs = DEFAULT_TIMEOUT_MS;
        }
        if (gotoTimeoutMs > MAX_TIMEOUT_MS) {
            gotoTimeoutMs = MAX_TIMEOUT_MS;
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? DEFAULT_BASE_URL : trimmed;
    }
}
