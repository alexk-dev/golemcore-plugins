package me.golemcore.plugins.golemcore.tavilysearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TavilySearchPluginConfig {

    private static final Set<String> SUPPORTED_TOPICS = Set.of("general", "news");
    private static final Set<String> SUPPORTED_SEARCH_DEPTHS = Set.of("basic", "advanced");
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int MAX_RESULTS = 20;

    @Builder.Default
    private Boolean enabled = false;

    private String apiKey;

    @Builder.Default
    private Integer defaultMaxResults = DEFAULT_MAX_RESULTS;

    @Builder.Default
    private String defaultTopic = "general";

    @Builder.Default
    private String defaultSearchDepth = "basic";

    @Builder.Default
    private Boolean includeAnswer = true;

    @Builder.Default
    private Boolean includeRawContent = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (defaultMaxResults == null || defaultMaxResults <= 0) {
            defaultMaxResults = DEFAULT_MAX_RESULTS;
        }
        if (defaultMaxResults > MAX_RESULTS) {
            defaultMaxResults = MAX_RESULTS;
        }
        if (defaultTopic == null || !SUPPORTED_TOPICS.contains(defaultTopic)) {
            defaultTopic = "general";
        }
        if (defaultSearchDepth == null || !SUPPORTED_SEARCH_DEPTHS.contains(defaultSearchDepth)) {
            defaultSearchDepth = "basic";
        }
        if (includeAnswer == null) {
            includeAnswer = true;
        }
        if (includeRawContent == null) {
            includeRawContent = false;
        }
    }
}
