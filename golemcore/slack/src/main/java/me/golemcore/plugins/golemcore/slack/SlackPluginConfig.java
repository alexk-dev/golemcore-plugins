package me.golemcore.plugins.golemcore.slack;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (replyInThread == null) {
            replyInThread = true;
        }
        botToken = normalizeSecret(botToken);
        appToken = normalizeSecret(appToken);
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
}
