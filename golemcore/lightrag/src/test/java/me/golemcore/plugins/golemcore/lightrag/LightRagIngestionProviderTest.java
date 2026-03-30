package me.golemcore.plugins.golemcore.lightrag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.golemcore.plugin.api.extension.model.rag.RagCorpusRef;
import me.golemcore.plugin.api.extension.model.rag.RagDocument;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionCapabilities;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class LightRagIngestionProviderTest {

    private LightRagPluginConfigService configService;
    private LightRagIngestionProvider provider;

    @BeforeEach
    void setUp() {
        configService = mock(LightRagPluginConfigService.class);
        provider = new LightRagIngestionProvider(configService);
    }

    @Test
    void shouldExposeCapabilitiesAndAvailability() {
        when(configService.getConfig()).thenReturn(LightRagPluginConfig.builder()
                .enabled(true)
                .url("http://localhost:9621")
                .build());

        RagIngestionCapabilities capabilities = provider.getCapabilities();

        assertEquals("golemcore/lightrag", provider.getProviderId());
        assertTrue(provider.isAvailable());
        assertFalse(capabilities.supportsDelete());
        assertFalse(capabilities.supportsReset());
        assertFalse(capabilities.supportsStatus());
        assertEquals(32, capabilities.maxBatchSize());
    }

    @Test
    void shouldReturnAcceptedResultWhenTargetIsDisabled() {
        when(configService.getConfig()).thenReturn(LightRagPluginConfig.builder()
                .enabled(false)
                .url("")
                .build());

        RagIngestionResult result = provider.upsertDocuments(
                new RagCorpusRef("notion", "Notion"),
                List.of(new RagDocument(
                        "doc-1",
                        "Projects/Todo",
                        "Projects/Todo",
                        "# Todo",
                        "https://notion.so/doc-1",
                        java.util.Map.of("source", "notion"))))
                .join();

        assertEquals("failed", result.status());
        assertEquals(0, result.acceptedDocuments());
        assertEquals(1, result.rejectedDocuments());
    }
}
