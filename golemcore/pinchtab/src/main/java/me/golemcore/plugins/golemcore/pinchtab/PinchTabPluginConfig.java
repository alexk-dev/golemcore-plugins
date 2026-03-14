package me.golemcore.plugins.golemcore.pinchtab;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinchTabPluginConfig {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:9867";
    private static final String DEFAULT_SNAPSHOT_FILTER = "interactive";
    private static final String DEFAULT_SNAPSHOT_FORMAT = "compact";
    private static final String DEFAULT_TEXT_MODE = "readability";
    private static final String DEFAULT_WAIT_FOR = "dom";
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_SCREENSHOT_QUALITY = 80;
    private static final int MIN_REQUEST_TIMEOUT_MS = 1_000;
    private static final int MAX_REQUEST_TIMEOUT_MS = 300_000;
    private static final int MIN_SCREENSHOT_QUALITY = 1;
    private static final int MAX_SCREENSHOT_QUALITY = 100;
    private static final Set<String> SUPPORTED_SNAPSHOT_FILTERS = Set.of("interactive", "all");
    private static final Set<String> SUPPORTED_SNAPSHOT_FORMATS = Set.of("compact", "text", "json", "yaml");
    private static final Set<String> SUPPORTED_TEXT_MODES = Set.of("readability", "raw");
    private static final Set<String> SUPPORTED_WAIT_FOR = Set.of("none", "dom", "networkidle");

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String baseUrl = DEFAULT_BASE_URL;

    private String apiToken;

    private String defaultInstanceId;

    @Builder.Default
    private Integer requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;

    @Builder.Default
    private String defaultSnapshotFilter = DEFAULT_SNAPSHOT_FILTER;

    @Builder.Default
    private String defaultSnapshotFormat = DEFAULT_SNAPSHOT_FORMAT;

    @Builder.Default
    private String defaultTextMode = DEFAULT_TEXT_MODE;

    @Builder.Default
    private String defaultWaitFor = DEFAULT_WAIT_FOR;

    @Builder.Default
    private Boolean defaultBlockImages = false;

    @Builder.Default
    private Integer defaultScreenshotQuality = DEFAULT_SCREENSHOT_QUALITY;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        } else {
            baseUrl = trimTrailingSlash(baseUrl.trim());
        }
        apiToken = normalizeOptional(apiToken);
        defaultInstanceId = normalizeOptional(defaultInstanceId);
        if (requestTimeoutMs == null || requestTimeoutMs < MIN_REQUEST_TIMEOUT_MS) {
            requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
        }
        if (requestTimeoutMs > MAX_REQUEST_TIMEOUT_MS) {
            requestTimeoutMs = MAX_REQUEST_TIMEOUT_MS;
        }
        if (defaultSnapshotFilter == null || !SUPPORTED_SNAPSHOT_FILTERS.contains(defaultSnapshotFilter)) {
            defaultSnapshotFilter = DEFAULT_SNAPSHOT_FILTER;
        }
        if (defaultSnapshotFormat == null || !SUPPORTED_SNAPSHOT_FORMATS.contains(defaultSnapshotFormat)) {
            defaultSnapshotFormat = DEFAULT_SNAPSHOT_FORMAT;
        }
        if (defaultTextMode == null || !SUPPORTED_TEXT_MODES.contains(defaultTextMode)) {
            defaultTextMode = DEFAULT_TEXT_MODE;
        }
        if (defaultWaitFor == null || !SUPPORTED_WAIT_FOR.contains(defaultWaitFor)) {
            defaultWaitFor = DEFAULT_WAIT_FOR;
        }
        if (defaultBlockImages == null) {
            defaultBlockImages = false;
        }
        if (defaultScreenshotQuality == null || defaultScreenshotQuality < MIN_SCREENSHOT_QUALITY) {
            defaultScreenshotQuality = DEFAULT_SCREENSHOT_QUALITY;
        }
        if (defaultScreenshotQuality > MAX_SCREENSHOT_QUALITY) {
            defaultScreenshotQuality = MAX_SCREENSHOT_QUALITY;
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? DEFAULT_BASE_URL : trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
