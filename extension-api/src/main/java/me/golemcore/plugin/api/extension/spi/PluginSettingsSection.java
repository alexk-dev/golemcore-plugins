package me.golemcore.plugin.api.extension.spi;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full backend-driven settings payload for one plugin section.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginSettingsSection {

    private String pluginId;
    private String pluginName;
    private String provider;
    private String sectionKey;
    private String routeKey;
    private String title;
    private String description;
    @Builder.Default
    private List<PluginSettingsField> fields = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> values = new LinkedHashMap<>();
    @Builder.Default
    private List<PluginSettingsBlock> blocks = new ArrayList<>();
    @Builder.Default
    private List<PluginSettingsAction> actions = new ArrayList<>();
}
