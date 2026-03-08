package me.golemcore.plugins.golemcore.perplexitysonar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerplexitySonarPluginConfig {

    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "sonar",
            "sonar-pro",
            "sonar-deep-research",
            "sonar-reasoning-pro");
    private static final Set<String> SUPPORTED_SEARCH_MODES = Set.of("web", "academic", "sec");

    @Builder.Default
    private Boolean enabled = false;

    private String apiKey;

    @Builder.Default
    private String defaultModel = "sonar";

    @Builder.Default
    private String defaultSearchMode = "web";

    @Builder.Default
    private Boolean returnRelatedQuestions = false;

    @Builder.Default
    private Boolean returnImages = false;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (defaultModel == null || !SUPPORTED_MODELS.contains(defaultModel)) {
            defaultModel = "sonar";
        }
        if (defaultSearchMode == null || !SUPPORTED_SEARCH_MODES.contains(defaultSearchMode)) {
            defaultSearchMode = "web";
        }
        if (returnRelatedQuestions == null) {
            returnRelatedQuestions = false;
        }
        if (returnImages == null) {
            returnImages = false;
        }
    }
}
