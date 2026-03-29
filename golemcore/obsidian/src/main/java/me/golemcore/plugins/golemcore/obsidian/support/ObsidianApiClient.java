package me.golemcore.plugins.golemcore.obsidian.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.obsidian.ObsidianPluginConfig;
import me.golemcore.plugins.golemcore.obsidian.ObsidianPluginConfigService;
import me.golemcore.plugins.golemcore.obsidian.model.ObsidianSearchResult;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ObsidianApiClient {

    private static final MediaType TEXT_MARKDOWN = MediaType.get("text/markdown; charset=utf-8");
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json; charset=utf-8");
    private static final String VAULT_SEGMENT = "vault";
    private static final String SEARCH_SEGMENT = "search";
    private static final String SIMPLE_SEGMENT = "simple";

    private final ObsidianPluginConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObsidianApiClient(ObsidianPluginConfigService configService) {
        this.configService = configService;
    }

    public List<String> listDirectory(String path) {
        try {
            Request request = new Request.Builder()
                    .url(buildVaultUrl(path, true))
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader())
                    .get()
                    .build();
            try (Response response = executeRequest(request)) {
                JsonNode root = readJsonResponse(response);
                JsonNode filesNode = root.path("files");
                if (!filesNode.isArray()) {
                    return List.of();
                }
                List<String> files = new ArrayList<>(filesNode.size());
                for (JsonNode fileNode : filesNode) {
                    files.add(fileNode.asText(""));
                }
                return List.copyOf(files);
            }
        } catch (IOException ex) {
            throw new ObsidianApiException(500, "Obsidian request failed: " + ex.getMessage());
        }
    }

    public String readNote(String path) {
        try {
            Request request = new Request.Builder()
                    .url(buildVaultUrl(path, false))
                    .header("Accept", "application/vnd.olrapi.note+json, text/markdown")
                    .header("Authorization", authorizationHeader())
                    .get()
                    .build();
            try (Response response = executeRequest(request);
                    ResponseBody body = response.body()) {
                String responseBody = body != null ? body.string() : "";
                ensureSuccess(response, responseBody);
                if (!hasText(responseBody)) {
                    return "";
                }
                JsonNode root = tryReadJson(responseBody);
                if (root != null && root.hasNonNull("content")) {
                    return root.path("content").asText("");
                }
                return responseBody;
            }
        } catch (IOException ex) {
            throw new ObsidianApiException(500, "Obsidian request failed: " + ex.getMessage());
        }
    }

    public void writeNote(String path, String content) {
        try {
            Request request = new Request.Builder()
                    .url(buildVaultUrl(path, false))
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader())
                    .header("Content-Type", TEXT_MARKDOWN.toString())
                    .put(RequestBody.create(content != null ? content : "", TEXT_MARKDOWN))
                    .build();
            try (Response response = executeRequest(request);
                    ResponseBody body = response.body()) {
                String responseBody = body != null ? body.string() : "";
                ensureSuccess(response, responseBody);
            }
        } catch (IOException ex) {
            throw new ObsidianApiException(500, "Obsidian request failed: " + ex.getMessage());
        }
    }

    public void deleteNote(String path) {
        try {
            Request request = new Request.Builder()
                    .url(buildVaultUrl(path, false))
                    .header("Authorization", authorizationHeader())
                    .delete()
                    .build();
            try (Response response = executeRequest(request);
                    ResponseBody body = response.body()) {
                String responseBody = body != null ? body.string() : "";
                ensureSuccess(response, responseBody);
            }
        } catch (IOException ex) {
            throw new ObsidianApiException(500, "Obsidian request failed: " + ex.getMessage());
        }
    }

    public List<ObsidianSearchResult> simpleSearch(String query, int contextLength) {
        if (!hasText(query)) {
            throw new IllegalArgumentException("Search query is required");
        }

        int effectiveContextLength = contextLength > 0 ? contextLength : getConfig().getDefaultSearchContextLength();
        try {
            Request request = new Request.Builder()
                    .url(buildSearchUrl(query, effectiveContextLength))
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader())
                    .post(RequestBody.create("", APPLICATION_JSON))
                    .build();
            try (Response response = executeRequest(request);
                    ResponseBody body = response.body()) {
                String responseBody = body != null ? body.string() : "";
                ensureSuccess(response, responseBody);
                if (!hasText(responseBody)) {
                    return List.of();
                }
                JsonNode root = objectMapper.readTree(responseBody);
                return parseSearchResults(root);
            }
        } catch (IOException ex) {
            throw new ObsidianApiException(500, "Obsidian request failed: " + ex.getMessage());
        }
    }

    protected Response executeRequest(Request request) throws IOException {
        return buildHttpClient(getConfig()).newCall(request).execute();
    }

    private ObsidianPluginConfig getConfig() {
        return configService.getConfig();
    }

    private HttpUrl buildVaultUrl(String relativePath, boolean directory) {
        ObsidianPluginConfig config = getConfig();
        HttpUrl baseUrl = parseBaseUrl(config.getBaseUrl());
        HttpUrl.Builder builder = Objects.requireNonNull(baseUrl).newBuilder().addPathSegment(VAULT_SEGMENT);
        for (String segment : splitPath(relativePath)) {
            builder.addPathSegment(segment);
        }
        if (directory) {
            builder.addPathSegment("");
        }
        return builder.build();
    }

    private HttpUrl buildSearchUrl(String query, int contextLength) {
        ObsidianPluginConfig config = getConfig();
        HttpUrl baseUrl = parseBaseUrl(config.getBaseUrl());
        return Objects.requireNonNull(baseUrl)
                .newBuilder()
                .addPathSegment(SEARCH_SEGMENT)
                .addPathSegment(SIMPLE_SEGMENT)
                .addPathSegment("")
                .addQueryParameter("query", query)
                .addQueryParameter("contextLength", String.valueOf(contextLength))
                .build();
    }

    private HttpUrl parseBaseUrl(String baseUrl) {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException("Invalid Obsidian base URL: " + baseUrl);
        }
        return parsed;
    }

    private List<String> splitPath(String relativePath) {
        if (!hasText(relativePath)) {
            return List.of();
        }
        String normalized = relativePath.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!hasText(normalized)) {
            return List.of();
        }
        String[] segments = normalized.split("/");
        List<String> values = new ArrayList<>(segments.length);
        for (String segment : segments) {
            if (hasText(segment)) {
                values.add(segment);
            }
        }
        return values;
    }

    private OkHttpClient buildHttpClient(ObsidianPluginConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .callTimeout(java.time.Duration.ofMillis(config.getTimeoutMs()))
                .connectTimeout(java.time.Duration.ofMillis(config.getTimeoutMs()))
                .readTimeout(java.time.Duration.ofMillis(config.getTimeoutMs()))
                .writeTimeout(java.time.Duration.ofMillis(config.getTimeoutMs()));
        HttpUrl baseUrl = parseBaseUrl(config.getBaseUrl());
        if (Boolean.TRUE.equals(config.getAllowInsecureTls()) && "https".equalsIgnoreCase(baseUrl.scheme())) {
            try {
                TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, new SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                HostnameVerifier hostnameVerifier = (hostname, session) -> true;
                builder.sslSocketFactory(sslSocketFactory, trustManager);
                builder.hostnameVerifier(hostnameVerifier);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to configure insecure TLS for Obsidian", ex);
            }
        }
        return builder.build();
    }

    private JsonNode readJsonResponse(Response response) throws IOException {
        try (ResponseBody body = response.body()) {
            String responseBody = body != null ? body.string() : "";
            ensureSuccess(response, responseBody);
            if (!hasText(responseBody)) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        }
    }

    private void ensureSuccess(Response response, String responseBody) {
        if (response.isSuccessful()) {
            return;
        }
        throw new ObsidianApiException(response.code(), extractErrorMessage(responseBody, response.code()));
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (!hasText(responseBody)) {
            return "HTTP " + statusCode;
        }
        JsonNode root = tryReadJson(responseBody);
        if (root != null) {
            String message = firstText(root.path("message").asText(null),
                    root.path("error").asText(null),
                    root.path("status").asText(null));
            if (hasText(message)) {
                return message;
            }
        }
        return responseBody.trim();
    }

    private JsonNode tryReadJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (IOException ignored) {
            return null;
        }
    }

    private List<ObsidianSearchResult> parseSearchResults(JsonNode root) {
        JsonNode resultsNode = root.path("results");
        if (resultsNode.isArray()) {
            return parseSearchResultsArray(resultsNode);
        }
        JsonNode matchesNode = root.path("matches");
        if (matchesNode.isArray()) {
            return parseSearchResultsArray(matchesNode);
        }
        if (root.isArray()) {
            return parseSearchResultsArray(root);
        }
        return List.of();
    }

    private List<ObsidianSearchResult> parseSearchResultsArray(JsonNode arrayNode) {
        List<ObsidianSearchResult> results = new ArrayList<>(arrayNode.size());
        for (JsonNode resultNode : arrayNode) {
            results.add(new ObsidianSearchResult(
                    firstText(resultNode.path("filename").asText(null),
                            resultNode.path("path").asText(null),
                            resultNode.path("file").asText(null),
                            resultNode.path("title").asText(null),
                            ""),
                    resultNode.path("score").isNumber() ? resultNode.path("score").asDouble() : 0.0d,
                    parseMatches(resultNode.path("matches"))));
        }
        return List.copyOf(results);
    }

    private List<ObsidianSearchResult.Match> parseMatches(JsonNode matchesNode) {
        if (!matchesNode.isArray()) {
            return List.of();
        }
        List<ObsidianSearchResult.Match> matches = new ArrayList<>(matchesNode.size());
        for (JsonNode matchNode : matchesNode) {
            matches.add(new ObsidianSearchResult.Match(
                    matchNode.path("context").asText(""),
                    matchNode.path("start").isNumber() ? matchNode.path("start").asInt() : null,
                    matchNode.path("end").isNumber() ? matchNode.path("end").asInt() : null));
        }
        return List.copyOf(matches);
    }

    private String authorizationHeader() {
        return "Bearer " + getConfig().getApiKey();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
