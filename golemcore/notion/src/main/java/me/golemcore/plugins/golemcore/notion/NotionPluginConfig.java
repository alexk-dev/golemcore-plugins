package me.golemcore.plugins.golemcore.notion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotionPluginConfig {

    static final String DEFAULT_BASE_URL = "https://api.notion.com";
    static final String DEFAULT_API_VERSION = "2026-03-11";
    static final int DEFAULT_TIMEOUT_MS = 30_000;
    static final int DEFAULT_MAX_READ_CHARS = 12_000;
    static final String DEFAULT_REINDEX_SCHEDULE_PRESET = "disabled";
    static final String DEFAULT_RAG_CORPUS_ID = "notion";

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String baseUrl = DEFAULT_BASE_URL;

    @Builder.Default
    private String apiVersion = DEFAULT_API_VERSION;

    private String apiKey;
    private String rootPageId;

    @Builder.Default
    private Integer timeoutMs = DEFAULT_TIMEOUT_MS;

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

    @Builder.Default
    private Boolean localIndexEnabled = false;

    @Builder.Default
    private String reindexSchedulePreset = DEFAULT_REINDEX_SCHEDULE_PRESET;

    @Builder.Default
    private String reindexCronExpression = "";

    @Builder.Default
    private Boolean ragSyncEnabled = false;

    @Builder.Default
    private String targetRagProviderId = "";

    @Builder.Default
    private String ragCorpusId = DEFAULT_RAG_CORPUS_ID;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        baseUrl = normalizeUrl(baseUrl, DEFAULT_BASE_URL);
        apiVersion = normalizeText(apiVersion, DEFAULT_API_VERSION);
        rootPageId = normalizeText(rootPageId, "");
        if (timeoutMs == null || timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
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
        if (localIndexEnabled == null) {
            localIndexEnabled = false;
        }
        reindexSchedulePreset = normalizeText(reindexSchedulePreset, DEFAULT_REINDEX_SCHEDULE_PRESET);
        reindexCronExpression = normalizeText(reindexCronExpression, "");
        if (ragSyncEnabled == null) {
            ragSyncEnabled = false;
        }
        targetRagProviderId = normalizeText(targetRagProviderId, "");
        ragCorpusId = normalizeText(ragCorpusId, DEFAULT_RAG_CORPUS_ID);
    }

    private String normalizeUrl(String value, String defaultValue) {
        String trimmed = normalizeText(value, defaultValue);
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
