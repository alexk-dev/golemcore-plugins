package me.golemcore.plugins.golemcore.supabase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupabasePluginConfig {

    static final String DEFAULT_SCHEMA = "public";
    static final String DEFAULT_SELECT = "*";
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 1000;

    @Builder.Default
    private Boolean enabled = false;

    private String projectUrl;
    private String apiKey;

    @Builder.Default
    private String defaultSchema = DEFAULT_SCHEMA;

    @Builder.Default
    private String defaultTable = "";

    @Builder.Default
    private String defaultSelect = DEFAULT_SELECT;

    @Builder.Default
    private Integer defaultLimit = DEFAULT_LIMIT;

    @Builder.Default
    private Boolean allowWrite = false;

    @Builder.Default
    private Boolean allowDelete = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        projectUrl = normalizeUrl(projectUrl, null);
        apiKey = normalizeText(apiKey, null);
        defaultSchema = normalizeText(defaultSchema, DEFAULT_SCHEMA);
        defaultTable = normalizeText(defaultTable, "");
        defaultSelect = normalizeText(defaultSelect, DEFAULT_SELECT);
        if (defaultLimit == null || defaultLimit <= 0) {
            defaultLimit = DEFAULT_LIMIT;
        }
        if (defaultLimit > MAX_LIMIT) {
            defaultLimit = MAX_LIMIT;
        }
        if (allowWrite == null) {
            allowWrite = false;
        }
        if (allowDelete == null) {
            allowDelete = false;
        }
    }

    private String normalizeUrl(String value, String defaultValue) {
        String trimmed = normalizeText(value, defaultValue);
        if (trimmed == null) {
            return null;
        }
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
