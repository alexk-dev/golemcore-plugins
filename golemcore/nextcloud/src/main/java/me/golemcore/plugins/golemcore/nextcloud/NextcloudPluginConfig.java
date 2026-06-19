package me.golemcore.plugins.golemcore.nextcloud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextcloudPluginConfig {

    static final String DEFAULT_BASE_URL = "https://nextcloud.example.com";
    static final int DEFAULT_TIMEOUT_MS = 30_000;
    static final int DEFAULT_MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024;
    static final int DEFAULT_MAX_INLINE_TEXT_CHARS = 12_000;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String baseUrl = DEFAULT_BASE_URL;

    private String username;
    private String appPassword;

    @Builder.Default
    private String rootPath = "";

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

    @Builder.Default
    private Boolean allowInsecureTls = false;

    @Builder.Default
    private Integer maxDownloadBytes = DEFAULT_MAX_DOWNLOAD_BYTES;

    @Builder.Default
    private Integer maxInlineTextChars = DEFAULT_MAX_INLINE_TEXT_CHARS;

    @Builder.Default
    private Boolean allowWrite = false;

    @Builder.Default
    private Boolean allowDelete = false;

    @Builder.Default
    private Boolean allowMove = false;

    @Builder.Default
    private Boolean allowCopy = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        baseUrl = normalizeUrl(baseUrl, DEFAULT_BASE_URL);
        username = normalizeText(username, "");
        rootPath = normalizeRootPath(rootPath);
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
        if (allowInsecureTls == null) {
            allowInsecureTls = false;
        }
        if (maxDownloadBytes == null || maxDownloadBytes <= 0) {
            maxDownloadBytes = DEFAULT_MAX_DOWNLOAD_BYTES;
        }
        if (maxInlineTextChars == null || maxInlineTextChars <= 0) {
            maxInlineTextChars = DEFAULT_MAX_INLINE_TEXT_CHARS;
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
        if (allowCopy == null) {
            allowCopy = false;
        }
    }

    private String normalizeUrl(String value, String defaultValue) {
        String trimmed = normalizeText(value, defaultValue);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private String normalizeRootPath(String value) {
        String candidate = normalizeText(value, "").replace('\\', '/');
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isBlank()) {
            return "";
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String rawSegment : candidate.split("/")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("rootPath must stay within the Nextcloud files root");
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
