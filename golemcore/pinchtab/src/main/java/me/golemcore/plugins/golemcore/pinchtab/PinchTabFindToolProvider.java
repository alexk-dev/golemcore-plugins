package me.golemcore.plugins.golemcore.pinchtab;

import com.fasterxml.jackson.databind.JsonNode;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolFailureKind;
import me.golemcore.plugin.api.extension.model.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PinchTabFindToolProvider extends AbstractPinchTabToolProvider {

    private static final String PARAM_QUERY = "query";
    private static final String PARAM_TAB_ID = "tab_id";
    private static final String PARAM_THRESHOLD = "threshold";
    private static final String PARAM_TOP_K = "top_k";
    private static final String PARAM_LEXICAL_WEIGHT = "lexical_weight";
    private static final String PARAM_EMBEDDING_WEIGHT = "embedding_weight";
    private static final String PARAM_EXPLAIN = "explain";

    public PinchTabFindToolProvider(PinchTabHttpClient client) {
        super(client);
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("pinchtab_find")
                .description("""
                        Find an element by natural-language description using PinchTab semantic matching.
                        The tool returns a best_ref you can pass directly into pinchtab_action.
                        """)
                .inputSchema(Map.of(
                        TYPE, TYPE_OBJECT,
                        PROPERTIES, Map.of(
                                PARAM_QUERY, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Natural-language description of the target element."),
                                PARAM_TAB_ID, Map.of(
                                        TYPE, TYPE_STRING,
                                        "description", "Optional tab id."),
                                PARAM_THRESHOLD, Map.of(
                                        TYPE, TYPE_NUMBER,
                                        "description", "Minimum similarity score."),
                                PARAM_TOP_K, Map.of(
                                        TYPE, TYPE_INTEGER,
                                        "description", "Maximum number of matches."),
                                PARAM_LEXICAL_WEIGHT, Map.of(
                                        TYPE, TYPE_NUMBER,
                                        "description", "Optional lexical score weight override."),
                                PARAM_EMBEDDING_WEIGHT, Map.of(
                                        TYPE, TYPE_NUMBER,
                                        "description", "Optional embedding score weight override."),
                                PARAM_EXPLAIN, Map.of(
                                        TYPE, TYPE_BOOLEAN,
                                        "description", "Include match explanation details.")),
                        REQUIRED, List.of(PARAM_QUERY)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> executeFind(parameters));
    }

    private ToolResult executeFind(Map<String, Object> parameters) {
        if (!isEnabled()) {
            return disabledResult();
        }
        String query = readString(parameters.get(PARAM_QUERY));
        if (!hasText(query)) {
            return ToolResult.failure(ToolFailureKind.EXECUTION_FAILED, "query is required");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query.trim());
            String tabId = readString(parameters.get(PARAM_TAB_ID));
            if (hasText(tabId)) {
                body.put("tabId", tabId.trim());
            }
            Double threshold = readDouble(parameters.get(PARAM_THRESHOLD));
            if (threshold != null && threshold > 0) {
                body.put("threshold", threshold);
            }
            Integer topK = readInteger(parameters.get(PARAM_TOP_K));
            if (topK != null && topK > 0) {
                body.put("topK", topK);
            }
            Double lexicalWeight = readDouble(parameters.get(PARAM_LEXICAL_WEIGHT));
            if (lexicalWeight != null && lexicalWeight > 0) {
                body.put("lexicalWeight", lexicalWeight);
            }
            Double embeddingWeight = readDouble(parameters.get(PARAM_EMBEDDING_WEIGHT));
            if (embeddingWeight != null && embeddingWeight > 0) {
                body.put("embeddingWeight", embeddingWeight);
            }
            if (readBoolean(parameters.get(PARAM_EXPLAIN), false)) {
                body.put("explain", true);
            }

            JsonNode root = getClient().postJson("/find", body);
            StringBuilder output = new StringBuilder("PinchTab find result for \"")
                    .append(query.trim())
                    .append('"');
            if (hasText(root.path("best_ref").asText())) {
                output.append("\nBest ref: ").append(root.path("best_ref").asText());
            }
            if (hasText(root.path("confidence").asText())) {
                output.append("\nConfidence: ").append(root.path("confidence").asText());
            }
            if (root.has("score")) {
                output.append("\nScore: ").append(root.path("score").asDouble());
            }
            JsonNode matches = root.path("matches");
            if (matches.isArray() && !matches.isEmpty()) {
                output.append("\n\nMatches:\n");
                for (JsonNode match : matches) {
                    output.append("- ").append(match.path("ref").asText("(unknown)"));
                    if (hasText(match.path("role").asText())) {
                        output.append(" role=").append(match.path("role").asText());
                    }
                    if (hasText(match.path("name").asText())) {
                        output.append(" text=").append(match.path("name").asText());
                    }
                    if (match.has("score")) {
                        output.append(" score=").append(match.path("score").asDouble());
                    }
                    output.append('\n');
                }
            }
            return ToolResult.success(output.toString(), getClient().toObject(root));
        } catch (Exception ex) {
            return failureResult("find", ex);
        }
    }
}
