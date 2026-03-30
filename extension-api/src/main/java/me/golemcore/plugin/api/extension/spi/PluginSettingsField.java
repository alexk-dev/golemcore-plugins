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
import java.util.List;

/**
 * Generic field descriptor for backend-driven plugin settings forms.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginSettingsField {

    private String key;
    private String type;
    private String label;
    private String description;
    private String placeholder;
    private Boolean required;
    private Boolean readOnly;
    private Boolean masked;
    private Boolean copyable;
    private Double min;
    private Double max;
    private Double step;
    @Builder.Default
    private List<PluginSettingsFieldOption> options = new ArrayList<>();
}
