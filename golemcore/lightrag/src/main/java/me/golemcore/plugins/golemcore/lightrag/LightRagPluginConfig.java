package me.golemcore.plugins.golemcore.lightrag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LightRagPluginConfig {

    @Builder.Default
    private Boolean enabled = false;

    @Builder.Default
    private String url = "http://localhost:9621";

    private String apiKey;

    @Builder.Default
    private String queryMode = "hybrid";

    @Builder.Default
    private Integer timeoutSeconds = 10;

    @Builder.Default
    private Integer indexMinLength = 50;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (url == null || url.isBlank()) {
            url = "http://localhost:9621";
        }
        if (queryMode == null || queryMode.isBlank()) {
            queryMode = "hybrid";
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
        if (indexMinLength == null || indexMinLength <= 0) {
            indexMinLength = 50;
        }
    }
}
