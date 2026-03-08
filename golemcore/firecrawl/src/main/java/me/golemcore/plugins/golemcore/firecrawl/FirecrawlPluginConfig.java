package me.golemcore.plugins.golemcore.firecrawl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirecrawlPluginConfig {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("markdown", "summary", "html", "rawHtml", "links");
    private static final String DEFAULT_FORMAT = "markdown";
    private static final int DEFAULT_MAX_AGE_MS = 172_800_000;
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_TIMEOUT_MS = 300_000;

    @Builder.Default
    private Boolean enabled = false;

    private String apiKey;

    @Builder.Default
    private String defaultFormat = DEFAULT_FORMAT;

    @Builder.Default
    private Boolean onlyMainContent = true;

    @Builder.Default
    private Integer maxAgeMs = DEFAULT_MAX_AGE_MS;

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (defaultFormat == null || !SUPPORTED_FORMATS.contains(defaultFormat)) {
            defaultFormat = DEFAULT_FORMAT;
        }
        if (onlyMainContent == null) {
            onlyMainContent = true;
        }
        if (maxAgeMs == null || maxAgeMs < 0) {
            maxAgeMs = DEFAULT_MAX_AGE_MS;
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        if (timeoutMs > MAX_TIMEOUT_MS) {
            timeoutMs = MAX_TIMEOUT_MS;
        }
    }
}
