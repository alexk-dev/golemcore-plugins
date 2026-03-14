package me.golemcore.plugins.golemcore.perplexitysonar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PerplexitySonarPluginConfigTest {

    @Test
    void shouldNormalizeUnsupportedAndNullValues() {
        PerplexitySonarPluginConfig config = PerplexitySonarPluginConfig.builder()
                .enabled(null)
                .apiKey("pplx-test-key")
                .defaultModel("unsupported-model")
                .defaultSearchMode("unsupported-search-mode")
                .returnRelatedQuestions(null)
                .returnImages(null)
                .build();

        config.normalize();

        assertFalse(config.getEnabled());
        assertEquals("sonar", config.getDefaultModel());
        assertEquals("web", config.getDefaultSearchMode());
        assertFalse(config.getReturnRelatedQuestions());
        assertFalse(config.getReturnImages());
    }
}
