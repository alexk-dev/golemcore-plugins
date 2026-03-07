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
 * Generic content block. Current supported types: {@code notice},
 * {@code table}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginSettingsBlock {

    private String type;
    private String key;
    private String title;
    private String description;
    private String variant;
    private String text;
    @Builder.Default
    private List<PluginSettingsTableColumn> columns = new ArrayList<>();
    @Builder.Default
    private List<PluginSettingsTableRow> rows = new ArrayList<>();
}
