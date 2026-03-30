package me.golemcore.plugin.api.runtime;

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

import me.golemcore.plugin.api.extension.model.rag.RagCorpusRef;
import me.golemcore.plugin.api.extension.model.rag.RagDocument;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionResult;
import me.golemcore.plugin.api.extension.model.rag.RagIngestionStatus;
import me.golemcore.plugin.api.runtime.model.RagIngestionTargetDescriptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface RagIngestionService {

    List<RagIngestionTargetDescriptor> listInstalledTargets();

    CompletableFuture<RagIngestionResult> upsertDocuments(
            String providerId,
            RagCorpusRef corpus,
            List<RagDocument> documents);

    CompletableFuture<RagIngestionResult> deleteDocuments(
            String providerId,
            RagCorpusRef corpus,
            List<String> documentIds);

    CompletableFuture<RagIngestionResult> resetCorpus(String providerId, RagCorpusRef corpus);

    CompletableFuture<RagIngestionStatus> getStatus(String providerId, RagCorpusRef corpus);
}
