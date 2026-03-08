package me.golemcore.plugins.golemcore.perplexitysonar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PerplexityAskToolProvider implements ToolProvider {

    private static final String TYPE = "type";
    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_ARRAY = "array";
    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ITEMS = "items";
    private static final String PARAM_QUESTION = "question";
    private static final String PARAM_MODEL = "model";
    private static final String PARAM_SYSTEM_PROMPT = "system_prompt";
    private static final String PARAM_SEARCH_MODE = "search_mode";
    private static final String PARAM_SEARCH_DOMAIN_FILTER = "search_domain_filter";
    private static final String PARAM_SEARCH_LANGUAGE_FILTER = "search_language_filter";
    private static final String PARAM_SEARCH_RECENCY_FILTER = "search_recency_filter";
    private static final String PARAM_MAX_TOKENS = "max_tokens";
    private static final String PARAM_TEMPERATURE = "temperature";
    private static final String PARAM_RETURN_RELATED_QUESTIONS = "return_related_questions";
    private static final String PARAM_RETURN_IMAGES = "return_images";
    private static final String PARAM_REASONING_EFFORT = "reasoning_effort";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "sonar",
            "sonar-pro",
            "sonar-deep-research",
            "sonar-reasoning-pro");
    private static final Set<String> SUPPORTED_SEARCH_MODES = Set.of("web", "academic", "sec");
    private static final Set<String> SUPPORTED_REASONING_EFFORTS = Set.of("minimal", "low", "medium", "high");
    private static final Set<String> SUPPORTED_RECENCY_FILTERS = Set.of("hour", "day", "week", "month", "year");

    private final PerplexitySonarPluginConfigService configService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isEnabled() {
        PerplexitySonarPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && hasText(config.getApiKey());
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PARAM_QUESTION, Map.of(
                TYPE, TYPE_STRING,
                "description", "The user question to send to Perplexity."));
        properties.put(PARAM_MODEL, Map.of(
                TYPE, TYPE_STRING,
                "description", "Sonar model override."));
        properties.put(PARAM_SYSTEM_PROMPT, Map.of(
                TYPE, TYPE_STRING,
                "description", "Optional system prompt for answer style or constraints."));
        properties.put(PARAM_SEARCH_MODE, Map.of(
                TYPE, TYPE_STRING,
                "description", "Search mode: web, academic, or sec."));
        properties.put(PARAM_SEARCH_DOMAIN_FILTER, Map.of(
                TYPE, TYPE_ARRAY,
                ITEMS, Map.of(TYPE, TYPE_STRING),
                "description", "Optional allowlist of domains to search."));
        properties.put(PARAM_SEARCH_LANGUAGE_FILTER, Map.of(
                TYPE, TYPE_ARRAY,
                ITEMS, Map.of(TYPE, TYPE_STRING),
                "description", "Optional language filters (e.g. en, es)."));
        properties.put(PARAM_SEARCH_RECENCY_FILTER, Map.of(
                TYPE, TYPE_STRING,
                "description", "Optional recency filter: hour, day, week, month, or year."));
        properties.put(PARAM_MAX_TOKENS, Map.of(
                TYPE, TYPE_INTEGER,
                "description", "Optional completion token cap."));
        properties.put(PARAM_TEMPERATURE, Map.of(
                TYPE, TYPE_NUMBER,
                "description", "Optional sampling temperature."));
        properties.put(PARAM_RETURN_RELATED_QUESTIONS, Map.of(
                TYPE, TYPE_BOOLEAN,
                "description", "Return suggested follow-up questions."));
        properties.put(PARAM_RETURN_IMAGES, Map.of(
                TYPE, TYPE_BOOLEAN,
                "description", "Return image URLs in the response payload."));
        properties.put(PARAM_REASONING_EFFORT, Map.of(
                TYPE, TYPE_STRING,
                "description", "Optional reasoning effort: minimal, low, medium, or high."));

        return ToolDefinition.builder()
                .name("perplexity_ask")
                .description("Ask a grounded question through Perplexity Sonar using a synchronous completion.")
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, properties,
                        REQUIRED, List.of(PARAM_QUESTION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeAsk(parameters));
    }

    protected Response executeRequest(Request request) throws IOException {
        return httpClient.newCall(request).execute();
    }

    protected void sleepBeforeRetry(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Perplexity retry interrupted", ex);
        }
    }

    private ToolResult executeAsk(Map<String, Object> parameters) {
        String question = readString(parameters.get(PARAM_QUESTION));
        if (!hasText(question)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Question is required");
        }
        if (!isEnabled()) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                    "Perplexity Sonar is disabled or API key is missing");
        }

        PerplexitySonarPluginConfig config = configService.getConfig();
        String model = normalizeModel(readString(parameters.get(PARAM_MODEL)), config.getDefaultModel());
        String systemPrompt = readString(parameters.get(PARAM_SYSTEM_PROMPT));
        String searchMode = normalizeSearchMode(readString(parameters.get(PARAM_SEARCH_MODE)),
                config.getDefaultSearchMode());
        List<String> domainFilter = readStringList(parameters.get(PARAM_SEARCH_DOMAIN_FILTER));
        List<String> languageFilter = readStringList(parameters.get(PARAM_SEARCH_LANGUAGE_FILTER));
        String recencyFilter = normalizeRecencyFilter(readString(parameters.get(PARAM_SEARCH_RECENCY_FILTER)));
        Integer maxTokens = readNullableInteger(parameters.get(PARAM_MAX_TOKENS));
        Double temperature = readNullableDouble(parameters.get(PARAM_TEMPERATURE));
        boolean returnRelatedQuestions = readBoolean(parameters.get(PARAM_RETURN_RELATED_QUESTIONS),
                config.getReturnRelatedQuestions());
        boolean returnImages = readBoolean(parameters.get(PARAM_RETURN_IMAGES), config.getReturnImages());
        String reasoningEffort = normalizeReasoningEffort(readString(parameters.get(PARAM_REASONING_EFFORT)));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                PerplexityResponse response = ask(question, model, systemPrompt, searchMode, domainFilter,
                        languageFilter, recencyFilter, maxTokens, temperature, returnRelatedQuestions,
                        returnImages, reasoningEffort);
                return buildSuccessResult(question, model, searchMode, response);
            } catch (PerplexityRequestException ex) {
                if (shouldRetry(ex.statusCode()) && attempt < MAX_RETRIES - 1) {
                    sleepBeforeRetry((long) (INITIAL_BACKOFF_MS * Math.pow(2, attempt)));
                    continue;
                }
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, mapErrorMessage(ex.statusCode(), ex));
            } catch (Exception ex) { // NOSONAR - tool I/O should degrade gracefully
                return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED,
                        "Perplexity request failed: " + ex.getMessage());
            }
        }
        return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Perplexity request failed");
    }

    private PerplexityResponse ask(
            String question,
            String model,
            String systemPrompt,
            String searchMode,
            List<String> domainFilter,
            List<String> languageFilter,
            String recencyFilter,
            Integer maxTokens,
            Double temperature,
            boolean returnRelatedQuestions,
            boolean returnImages,
            String reasoningEffort) throws IOException {
        PerplexitySonarPluginConfig config = configService.getConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        requestBody.put("messages", buildMessages(question, systemPrompt));
        requestBody.put(PARAM_SEARCH_MODE, searchMode);
        requestBody.put(PARAM_RETURN_RELATED_QUESTIONS, returnRelatedQuestions);
        requestBody.put(PARAM_RETURN_IMAGES, returnImages);
        if (!domainFilter.isEmpty()) {
            requestBody.put(PARAM_SEARCH_DOMAIN_FILTER, domainFilter);
        }
        if (!languageFilter.isEmpty()) {
            requestBody.put(PARAM_SEARCH_LANGUAGE_FILTER, languageFilter);
        }
        if (recencyFilter != null) {
            requestBody.put(PARAM_SEARCH_RECENCY_FILTER, recencyFilter);
        }
        if (maxTokens != null && maxTokens > 0) {
            requestBody.put(PARAM_MAX_TOKENS, maxTokens);
        }
        if (temperature != null) {
            requestBody.put(PARAM_TEMPERATURE, temperature);
        }
        if (reasoningEffort != null) {
            requestBody.put(PARAM_REASONING_EFFORT, reasoningEffort);
        }

        Request request = new Request.Builder()
                .url("https://api.perplexity.ai/chat/completions")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), APPLICATION_JSON))
                .build();

        try (Response response = executeRequest(request);
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new PerplexityRequestException(response.code(), "HTTP " + response.code());
            }
            return objectMapper.readValue(body.string(), PerplexityResponse.class);
        }
    }

    private ToolResult buildSuccessResult(String question, String model, String searchMode,
            PerplexityResponse response) {
        String content = extractAnswer(response);
        if (!hasText(content)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "Perplexity returned an empty answer");
        }

        StringBuilder output = new StringBuilder();
        output.append("Perplexity answer for \"")
                .append(question)
                .append("\" (model=")
                .append(model)
                .append(", search_mode=")
                .append(searchMode)
                .append(")\n\n")
                .append(content);

        List<PerplexitySearchResult> searchResults = response.getSearchResults() != null
                ? response.getSearchResults()
                : List.of();
        if (!searchResults.isEmpty()) {
            output.append("\n\nSources:\n");
            for (int index = 0; index < searchResults.size(); index++) {
                PerplexitySearchResult result = searchResults.get(index);
                output.append(index + 1)
                        .append(". ")
                        .append(hasText(result.getTitle()) ? result.getTitle() : result.getUrl())
                        .append('\n');
                if (hasText(result.getUrl())) {
                    output.append(result.getUrl()).append('\n');
                }
                if (hasText(result.getDate())) {
                    output.append("Date: ").append(result.getDate()).append('\n');
                }
                if (hasText(result.getSnippet())) {
                    output.append(result.getSnippet()).append('\n');
                }
                output.append('\n');
            }
        }

        if (response.getRelatedQuestions() != null && !response.getRelatedQuestions().isEmpty()) {
            output.append("Related questions:\n");
            for (String relatedQuestion : response.getRelatedQuestions()) {
                if (hasText(relatedQuestion)) {
                    output.append("- ").append(relatedQuestion).append('\n');
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(PARAM_QUESTION, question);
        data.put("model", model);
        data.put(PARAM_SEARCH_MODE, searchMode);
        data.put("answer", content);
        data.put("search_results", mapSearchResults(searchResults));
        data.put("related_questions",
                response.getRelatedQuestions() != null ? response.getRelatedQuestions() : List.of());
        data.put("images", response.getImages() != null ? response.getImages() : List.of());
        data.put("usage", mapUsage(response.getUsage()));
        return ToolResult.success(output.toString().trim(), data);
    }

    private List<Map<String, Object>> mapSearchResults(List<PerplexitySearchResult> searchResults) {
        return searchResults.stream()
                .map(result -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", result.getTitle() != null ? result.getTitle() : "");
                    item.put("url", result.getUrl() != null ? result.getUrl() : "");
                    item.put("date", result.getDate() != null ? result.getDate() : "");
                    item.put("snippet", result.getSnippet() != null ? result.getSnippet() : "");
                    return item;
                })
                .toList();
    }

    private Map<String, Object> mapUsage(PerplexityUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("prompt_tokens", usage.getPromptTokens());
        mapped.put("completion_tokens", usage.getCompletionTokens());
        mapped.put("total_tokens", usage.getTotalTokens());
        return mapped;
    }

    private String extractAnswer(PerplexityResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        PerplexityChoice choice = response.getChoices().getFirst();
        if (choice == null || choice.getMessage() == null) {
            return null;
        }
        return choice.getMessage().getContent();
    }

    private List<Map<String, String>> buildMessages(String question, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (hasText(systemPrompt)) {
            messages.add(Map.of(
                    "role", "system",
                    "content", systemPrompt));
        }
        messages.add(Map.of(
                "role", "user",
                "content", question));
        return messages;
    }

    private String mapErrorMessage(int statusCode, PerplexityRequestException exception) {
        return switch (statusCode) {
        case 401, 403 -> "Perplexity authentication failed";
        case 422 -> "Perplexity rejected the request parameters";
        case 429 -> "Perplexity rate limit exceeded";
        default -> "Perplexity request failed: " + exception.getMessage();
        };
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String normalizeModel(String value, String defaultValue) {
        if (value != null && SUPPORTED_MODELS.contains(value)) {
            return value;
        }
        if (defaultValue != null && SUPPORTED_MODELS.contains(defaultValue)) {
            return defaultValue;
        }
        return "sonar";
    }

    private String normalizeSearchMode(String value, String defaultValue) {
        if (value != null && SUPPORTED_SEARCH_MODES.contains(value)) {
            return value;
        }
        if (defaultValue != null && SUPPORTED_SEARCH_MODES.contains(defaultValue)) {
            return defaultValue;
        }
        return "web";
    }

    private String normalizeReasoningEffort(String value) {
        if (value != null && SUPPORTED_REASONING_EFFORTS.contains(value)) {
            return value;
        }
        return null;
    }

    private String normalizeRecencyFilter(String value) {
        if (value != null && SUPPORTED_RECENCY_FILTERS.contains(value)) {
            return value;
        }
        return null;
    }

    private List<String> readStringList(Object value) {
        Set<String> normalized = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String text = readString(item);
                if (hasText(text)) {
                    normalized.add(text.trim());
                }
            }
        } else if (value instanceof String text) {
            String[] parts = text.split("[,\\n]");
            for (String part : parts) {
                if (hasText(part)) {
                    normalized.add(part.trim());
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private Integer readNullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double readNullableDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String readString(Object value) {
        return value instanceof String text ? text.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class PerplexityRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int httpStatusCode;

        private PerplexityRequestException(int statusCode, String message) {
            super(message);
            this.httpStatusCode = statusCode;
        }

        private int statusCode() {
            return httpStatusCode;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PerplexityResponse {

        @JsonProperty("choices")
        private List<PerplexityChoice> choices;

        @JsonProperty("search_results")
        private List<PerplexitySearchResult> searchResults;

        @JsonProperty("related_questions")
        private List<String> relatedQuestions;

        @JsonProperty("images")
        private List<String> images;

        @JsonProperty("usage")
        private PerplexityUsage usage;

        public List<PerplexityChoice> getChoices() {
            return choices;
        }

        public List<PerplexitySearchResult> getSearchResults() {
            return searchResults;
        }

        public List<String> getRelatedQuestions() {
            return relatedQuestions;
        }

        public List<String> getImages() {
            return images;
        }

        public PerplexityUsage getUsage() {
            return usage;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PerplexityChoice {

        @JsonProperty("message")
        private PerplexityMessage message;

        public PerplexityMessage getMessage() {
            return message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PerplexityMessage {

        @JsonProperty("content")
        private String content;

        public String getContent() {
            return content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PerplexitySearchResult {

        @JsonProperty("title")
        private String title;

        @JsonProperty("url")
        private String url;

        @JsonProperty("date")
        private String date;

        @JsonProperty("snippet")
        private String snippet;

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getDate() {
            return date;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PerplexityUsage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }
    }
}
