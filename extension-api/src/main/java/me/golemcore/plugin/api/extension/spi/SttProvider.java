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

import me.golemcore.plugin.api.extension.model.AudioFormat;
import me.golemcore.plugin.api.extension.port.outbound.VoicePort;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Speech-to-text provider contributed by a plugin.
 */
public interface SttProvider {

    String getProviderId();

    default Set<String> getAliases() {
        return Set.of();
    }

    boolean isAvailable();

    CompletableFuture<VoicePort.TranscriptionResult> transcribe(byte[] audioData, AudioFormat format);
}
