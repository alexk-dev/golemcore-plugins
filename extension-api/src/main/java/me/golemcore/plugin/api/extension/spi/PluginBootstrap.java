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

/**
 * ServiceLoader entry point for external plugins.
 *
 * <p>
 * The host resolves this interface from each plugin JAR, then creates a
 * dedicated child Spring context using the returned configuration class.
 */
public interface PluginBootstrap {

    /**
     * Static metadata for the plugin artifact.
     */
    PluginDescriptor descriptor();

    /**
     * Spring configuration class that bootstraps plugin beans.
     */
    Class<?> configurationClass();
}
