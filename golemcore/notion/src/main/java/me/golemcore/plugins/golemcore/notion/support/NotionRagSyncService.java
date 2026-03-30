package me.golemcore.plugins.golemcore.notion.support;

import me.golemcore.plugin.api.extension.model.rag.RagCorpusRef;
import me.golemcore.plugin.api.extension.model.rag.RagDocument;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionCapabilities;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionResult;
import me.golemcore.plugin.api.runtime.RagIngestionService;
import me.golemcore.plugin.api.runtime.model.RagIngestionTargetDescriptor;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NotionRagSyncService {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final NotionPluginConfigService configService;
    private final NotionApiClient apiClient;
    private final RagIngestionService ragIngestionService;
    private final AtomicBoolean fullReindexRequired = new AtomicBoolean(false);

    public NotionRagSyncService(
            NotionPluginConfigService configService,
            NotionApiClient apiClient,
            RagIngestionService ragIngestionService) {
        this.configService = configService;
        this.apiClient = apiClient;
        this.ragIngestionService = ragIngestionService;
    }

    public void upsertDocument(String pageId, String path, String title, String content, String url) {
        if (!isSyncConfigured() || !hasText(pageId)) {
            return;
        }
        RagDocument document = toDocument(pageId, path, title, content, url);
        ragIngestionService.upsertDocuments(
                configService.getConfig().getTargetRagProviderId(),
                corpusRef(),
                List.of(document)).join();
    }

    public void deleteDocument(String pageId) {
        if (!isSyncConfigured() || !hasText(pageId)) {
            return;
        }
        RagIngestionCapabilities capabilities = targetCapabilities().orElse(null);
        if (capabilities == null || !capabilities.supportsDelete()) {
            fullReindexRequired.set(true);
            return;
        }
        ragIngestionService.deleteDocuments(
                configService.getConfig().getTargetRagProviderId(),
                corpusRef(),
                List.of(pageId)).join();
    }

    public int reindexAll() {
        if (!isSyncConfigured()) {
            return 0;
        }
        NotionPluginConfig config = configService.getConfig();
        if (!hasText(config.getRootPageId())) {
            throw new IllegalStateException("Root page ID is not configured.");
        }
        RagIngestionTargetDescriptor target = targetDescriptor().orElseThrow(
                () -> new IllegalStateException(
                        "Configured RAG target is not installed: " + config.getTargetRagProviderId()));
        RagCorpusRef corpus = corpusRef();
        if (target.capabilities().supportsReset()) {
            ragIngestionService.resetCorpus(target.providerId(), corpus).join();
        }

        List<RagDocument> documents = new ArrayList<>();
        crawlDocuments(
                config.getRootPageId(),
                "",
                apiClient.retrievePageTitle(config.getRootPageId()),
                "",
                documents);

        int batchSize = target.capabilities().maxBatchSize() > 0
                ? target.capabilities().maxBatchSize()
                : DEFAULT_BATCH_SIZE;
        int acceptedDocuments = 0;
        for (int start = 0; start < documents.size(); start += batchSize) {
            List<RagDocument> batch = documents.subList(start, Math.min(documents.size(), start + batchSize));
            RagIngestionResult result = ragIngestionService.upsertDocuments(
                    target.providerId(),
                    corpus,
                    List.copyOf(batch)).join();
            acceptedDocuments += Math.max(0, result.acceptedDocuments());
        }
        fullReindexRequired.set(false);
        return acceptedDocuments;
    }

    boolean isFullReindexRequired() {
        return fullReindexRequired.get();
    }

    private void crawlDocuments(
            String pageId,
            String path,
            String title,
            String url,
            List<RagDocument> documents) {
        documents.add(toDocument(
                pageId,
                path,
                title != null ? title : "",
                apiClient.retrievePageMarkdown(pageId),
                url != null ? url : ""));
        for (NotionPageSummary child : apiClient.listChildPages(pageId)) {
            String childPath = path.isBlank() ? child.title() : path + "/" + child.title();
            crawlDocuments(child.id(), childPath, child.title(), child.url(), documents);
        }
    }

    private Optional<RagIngestionTargetDescriptor> targetDescriptor() {
        String providerId = configService.getConfig().getTargetRagProviderId();
        if (!hasText(providerId)) {
            return Optional.empty();
        }
        return ragIngestionService.listInstalledTargets().stream()
                .filter(target -> providerId.equals(target.providerId()))
                .findFirst();
    }

    private Optional<RagIngestionCapabilities> targetCapabilities() {
        return targetDescriptor().map(RagIngestionTargetDescriptor::capabilities);
    }

    private RagCorpusRef corpusRef() {
        String corpusId = configService.getConfig().getRagCorpusId();
        return new RagCorpusRef(corpusId, corpusId);
    }

    private RagDocument toDocument(String pageId, String path, String title, String content, String url) {
        return new RagDocument(
                pageId,
                title != null ? title : "",
                path != null ? path : "",
                content != null ? content : "",
                url != null ? url : "",
                Map.of(
                        "source", "notion",
                        "page_id", pageId));
    }

    private boolean isSyncConfigured() {
        NotionPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getRagSyncEnabled()) && hasText(config.getTargetRagProviderId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
