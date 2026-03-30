package me.golemcore.plugins.golemcore.lightrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.rag.RagCorpusRef;
import me.golemcore.plugin.api.extension.model.rag.RagDocument;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionCapabilities;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionResult;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionStatus;
import me.golemcore.plugin.api.extension.spi.RagIngestionProvider;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class LightRagIngestionProvider implements RagIngestionProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final RagIngestionCapabilities CAPABILITIES = new RagIngestionCapabilities(false, false, false, 32);

    private final LightRagPluginConfigService configService;
    private final ObjectMapper objectMapper;

    public LightRagIngestionProvider(LightRagPluginConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getProviderId() {
        return LightRagPluginConfigService.PLUGIN_ID;
    }

    @Override
    public boolean isAvailable() {
        LightRagPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && config.getUrl() != null && !config.getUrl().isBlank();
    }

    @Override
    public RagIngestionCapabilities getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletableFuture<RagIngestionResult> upsertDocuments(RagCorpusRef corpus, List<RagDocument> documents) {
        return CompletableFuture.supplyAsync(() -> doUpsert(corpus, documents));
    }

    @Override
    public CompletableFuture<RagIngestionResult> deleteDocuments(RagCorpusRef corpus, List<String> documentIds) {
        return CompletableFuture.completedFuture(new RagIngestionResult(
                "failed",
                0,
                documentIds != null ? documentIds.size() : 0,
                null,
                "LightRAG ingestion does not support deleting documents via plugin API."));
    }

    @Override
    public CompletableFuture<RagIngestionResult> resetCorpus(RagCorpusRef corpus) {
        return CompletableFuture.completedFuture(new RagIngestionResult(
                "failed",
                0,
                0,
                null,
                "LightRAG ingestion does not support corpus reset via plugin API."));
    }

    @Override
    public CompletableFuture<RagIngestionStatus> getStatus(RagCorpusRef corpus) {
        return CompletableFuture.completedFuture(new RagIngestionStatus(
                "unknown",
                "LightRAG ingestion status is not exposed via plugin API.",
                0,
                0,
                0,
                null));
    }

    private RagIngestionResult doUpsert(RagCorpusRef corpus, List<RagDocument> documents) {
        List<RagDocument> normalizedDocuments = documents != null ? documents : List.of();
        if (!isAvailable()) {
            return new RagIngestionResult(
                    "failed",
                    0,
                    normalizedDocuments.size(),
                    null,
                    "LightRAG is not enabled or URL is not configured.");
        }
        if (normalizedDocuments.isEmpty()) {
            return new RagIngestionResult("accepted", 0, 0, null, "No documents to ingest.");
        }

        int accepted = 0;
        int rejected = 0;
        String lastJobId = null;
        for (RagDocument document : normalizedDocuments) {
            if (!hasText(document.content())) {
                rejected++;
                continue;
            }
            try {
                lastJobId = ingestDocument(corpus, document);
                accepted++;
            } catch (IOException | IllegalStateException ex) {
                rejected++;
            }
        }
        String status = rejected == 0 ? "accepted" : accepted == 0 ? "failed" : "partial";
        String message = rejected == 0
                ? "LightRAG accepted all documents for ingestion."
                : "LightRAG accepted " + accepted + " document(s) and rejected " + rejected + ".";
        return new RagIngestionResult(status, accepted, rejected, lastJobId, message);
    }

    private String ingestDocument(RagCorpusRef corpus, RagDocument document) throws IOException {
        LightRagPluginConfig config = configService.getConfig();
        String url = stripTrailingSlash(config.getUrl()) + "/documents/text";
        String payload = objectMapper.writeValueAsString(Map.of(
                "text", document.content(),
                "file_source", fileSource(corpus, document)));
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, JSON));
        addApiKeyHeader(requestBuilder, config);
        try (Response response = client(config).newCall(requestBuilder.build()).execute();
                ResponseBody responseBody = response.body()) {
            String rawBody = responseBody.string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("LightRAG ingestion failed with status " + response.code());
            }
            return parseTrackId(rawBody);
        }
    }

    private String parseTrackId(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawBody).path("track_id").asText(null);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String fileSource(RagCorpusRef corpus, RagDocument document) {
        String corpusPrefix = corpus != null && hasText(corpus.corpusId()) ? corpus.corpusId().trim() + "/" : "";
        if (hasText(document.path())) {
            return corpusPrefix + document.path().trim();
        }
        if (hasText(document.title())) {
            return corpusPrefix + document.title().trim();
        }
        return corpusPrefix + document.documentId();
    }

    private OkHttpClient client(LightRagPluginConfig config) {
        int timeoutSeconds = config.getTimeoutSeconds();
        return new OkHttpClient.Builder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    private void addApiKeyHeader(Request.Builder builder, LightRagPluginConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
