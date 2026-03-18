package me.golemcore.plugins.golemcore.slack;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlackPluginConfig {

    @Builder.Default
    private Boolean enabled = false;

    private String botToken;
    private String appToken;

    @Builder.Default
    private Boolean replyInThread = true;

    @Builder.Default
    private List<String> allowedUserIds = new ArrayList<>();

    @Builder.Default
    private List<String> allowedChannelIds = new ArrayList<>();

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (replyInThread == null) {
            replyInThread = true;
        }
        botToken = normalizeSecret(botToken);
        appToken = normalizeSecret(appToken);
        allowedUserIds = normalizeIdentifiers(allowedUserIds);
        allowedChannelIds = normalizeIdentifiers(allowedChannelIds);
    }

    @JsonIgnore
    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank()
                && appToken != null && !appToken.isBlank();
    }

    private String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private List<String> normalizeIdentifiers(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String candidate = value.trim();
            if (!candidate.isBlank()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }
}
