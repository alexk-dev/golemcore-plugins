package me.golemcore.plugins.golemcore.brain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrainPluginConfig {

    private static final int DEFAULT_INTELLISEARCH_LIMIT = 5;
    private static final int MAX_INTELLISEARCH_LIMIT = 20;

    @Builder.Default
    private Boolean enabled = false;

    private String baseUrl;
    private String apiToken;

    @Builder.Default
    private String defaultSpaceSlug = "default";

    private String dynamicApiSlug;

    @Builder.Default
    private Integer defaultIntellisearchLimit = DEFAULT_INTELLISEARCH_LIMIT;

    @Builder.Default
    private Boolean allowWrite = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        baseUrl = trimToNull(baseUrl);
        if (baseUrl != null) {
            baseUrl = stripTrailingSlash(baseUrl);
        }
        apiToken = trimToNull(apiToken);
        defaultSpaceSlug = normalizeDefaultSpace(defaultSpaceSlug);
        dynamicApiSlug = trimToNull(dynamicApiSlug);
        if (defaultIntellisearchLimit == null || defaultIntellisearchLimit <= 0) {
            defaultIntellisearchLimit = DEFAULT_INTELLISEARCH_LIMIT;
        }
        if (defaultIntellisearchLimit > MAX_INTELLISEARCH_LIMIT) {
            defaultIntellisearchLimit = MAX_INTELLISEARCH_LIMIT;
        }
        if (allowWrite == null) {
            allowWrite = false;
        }
    }

    private String normalizeDefaultSpace(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : "default";
    }

    private String stripTrailingSlash(String value) {
        String stripped = value;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
