package me.golemcore.plugins.golemcore.bravesearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BraveSearchPluginConfig {

    @Builder.Default
    private Boolean enabled = false;

    private String apiKey;

    @Builder.Default
    private Integer defaultCount = 5;

    public void normalize() {
        if (enabled == null) {
            enabled = false;
        }
        if (defaultCount == null || defaultCount <= 0) {
            defaultCount = 5;
        }
        if (defaultCount > 20) {
            defaultCount = 20;
        }
    }
}
