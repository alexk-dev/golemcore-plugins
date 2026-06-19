package me.golemcore.plugins.golemcore.s3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class S3PluginConfig {

    static final String DEFAULT_ENDPOINT = "https://play.min.io";
    static final String DEFAULT_REGION = "us-east-1";
    static final int DEFAULT_TIMEOUT_MS = 30_000;
    static final int DEFAULT_MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024;
    static final int DEFAULT_MAX_INLINE_TEXT_CHARS = 12_000;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String endpoint = DEFAULT_ENDPOINT;

    @Builder.Default
    private String region = DEFAULT_REGION;

    private String accessKey;
    private String secretKey;

    @Builder.Default
    private String bucket = "";

    @Builder.Default
    private String rootPrefix = "";

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

    @Builder.Default
    private Boolean allowInsecureTls = false;

    @Builder.Default
    private Integer maxDownloadBytes = DEFAULT_MAX_DOWNLOAD_BYTES;

    @Builder.Default
    private Integer maxInlineTextChars = DEFAULT_MAX_INLINE_TEXT_CHARS;

    @Builder.Default
    private Boolean autoCreateBucket = false;

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
        endpoint = normalizeEndpoint(endpoint, DEFAULT_ENDPOINT);
        region = normalizeText(region, DEFAULT_REGION);
        bucket = normalizeText(bucket, "");
        rootPrefix = normalizePath(rootPrefix);
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
        if (autoCreateBucket == null) {
            autoCreateBucket = false;
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

    private String normalizeEndpoint(String value, String defaultValue) {
        String trimmed = normalizeText(value, defaultValue);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private String normalizePath(String value) {
        String candidate = normalizeText(value, "").replace('\\', '/');
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        while (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        String[] rawSegments = candidate.isBlank() ? new String[0] : candidate.split("/");
        StringBuilder normalized = new StringBuilder();
        for (String rawSegment : rawSegments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("rootPrefix must stay within the configured bucket root");
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
