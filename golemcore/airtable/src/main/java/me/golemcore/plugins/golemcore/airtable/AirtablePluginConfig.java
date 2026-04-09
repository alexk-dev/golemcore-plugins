package me.golemcore.plugins.golemcore.airtable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirtablePluginConfig {

    static final String DEFAULT_API_BASE_URL = "https://api.airtable.com";
    static final int DEFAULT_MAX_RECORDS = 20;
    static final int MAX_RECORDS_LIMIT = 100;

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String apiBaseUrl = DEFAULT_API_BASE_URL;

    private String apiToken;
    private String baseId;

    @Builder.Default
    private String defaultTable = "";

    @Builder.Default
    private String defaultView = "";

    @Builder.Default
    private Integer defaultMaxRecords = DEFAULT_MAX_RECORDS;

    @Builder.Default
    private Boolean allowWrite = false;

    @Builder.Default
    private Boolean allowDelete = false;

    @Builder.Default
    private Boolean typecast = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        apiBaseUrl = normalizeUrl(apiBaseUrl, DEFAULT_API_BASE_URL);
        apiToken = normalizeText(apiToken, null);
        baseId = normalizeText(baseId, null);
        defaultTable = normalizeText(defaultTable, "");
        defaultView = normalizeText(defaultView, "");
        if (defaultMaxRecords == null || defaultMaxRecords <= 0) {
            defaultMaxRecords = DEFAULT_MAX_RECORDS;
        }
        if (defaultMaxRecords > MAX_RECORDS_LIMIT) {
            defaultMaxRecords = MAX_RECORDS_LIMIT;
        }
        if (allowWrite == null) {
            allowWrite = false;
        }
        if (allowDelete == null) {
            allowDelete = false;
        }
        if (typecast == null) {
            typecast = false;
        }
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
