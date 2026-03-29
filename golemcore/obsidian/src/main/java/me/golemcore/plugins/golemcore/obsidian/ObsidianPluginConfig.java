package me.golemcore.plugins.golemcore.obsidian;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObsidianPluginConfig {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:27123";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_SEARCH_CONTEXT_LENGTH = 100;
    private static final int DEFAULT_MAX_READ_CHARS = 12_000;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String baseUrl = DEFAULT_BASE_URL;
    private String apiKey;

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

    @Builder.Default
    private Boolean allowInsecureTls = false;

    @Builder.Default
    private Integer defaultSearchContextLength = DEFAULT_SEARCH_CONTEXT_LENGTH;

    @Builder.Default
    private Integer maxReadChars = DEFAULT_MAX_READ_CHARS;

    @Builder.Default
    private Boolean allowWrite = false;

    @Builder.Default
    private Boolean allowDelete = false;

    @Builder.Default
    private Boolean allowMove = false;

    @Builder.Default
    private Boolean allowRename = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        } else {
            baseUrl = trimTrailingSlash(baseUrl.trim());
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        if (allowInsecureTls == null) {
            allowInsecureTls = false;
        }
        if (defaultSearchContextLength == null || defaultSearchContextLength <= 0) {
            defaultSearchContextLength = DEFAULT_SEARCH_CONTEXT_LENGTH;
        }
        if (maxReadChars == null || maxReadChars <= 0) {
            maxReadChars = DEFAULT_MAX_READ_CHARS;
        }
        if (allowWrite == null) {
            allowWrite = false;
        }
        if (allowDelete == null) {
            allowDelete = false;
        }
        if (allowMove == null) {
            allowMove = false;
        }
        if (allowRename == null) {
            allowRename = false;
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
