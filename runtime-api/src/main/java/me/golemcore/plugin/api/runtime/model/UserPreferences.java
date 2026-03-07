package me.golemcore.plugin.api.runtime.model;

/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User preferences exposed to plugins.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Builder.Default
    private String language = "en";

    @Builder.Default
    private boolean notificationsEnabled = true;

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private String modelTier = null;

    @Builder.Default
    private boolean tierForce = false;

    @Builder.Default
    private Map<String, TierOverride> tierOverrides = new HashMap<>();

    @Builder.Default
    private WebhookConfig webhooks = new WebhookConfig();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierOverride {
        private String model;
        private String reasoning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebhookConfig {
        @Builder.Default
        private boolean enabled = false;

        private Secret token;

        @Builder.Default
        private int maxPayloadSize = 65536;

        @Builder.Default
        private int defaultTimeoutSeconds = 300;

        @Builder.Default
        private List<HookMapping> mappings = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HookMapping {
        private String name;

        @Builder.Default
        private String action = "wake";

        @Builder.Default
        private String authMode = "bearer";

        private String hmacHeader;
        private Secret hmacSecret;
        private String hmacPrefix;
        private String messageTemplate;
        private String model;

        @Builder.Default
        private boolean deliver = false;

        private String channel;
        private String to;
    }
}
