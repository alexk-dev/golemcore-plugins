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

import java.util.List;
import java.util.Map;

/**
 * Extension point for plugin-driven dashboard settings.
 */
public interface PluginSettingsContributor {

    /**
     * The plugin id that owns these sections.
     */
    String getPluginId();

    /**
     * Lightweight catalog metadata used to populate the settings catalog.
     */
    List<PluginSettingsCatalogItem> getCatalogItems();

    /**
     * Resolve the full section payload for the requested section key.
     */
    PluginSettingsSection getSection(String sectionKey);

    /**
     * Persist user-updated field values and return the refreshed section payload.
     */
    PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values);

    /**
     * Execute a plugin-specific action and return a short operation result.
     */
    PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload);
}
