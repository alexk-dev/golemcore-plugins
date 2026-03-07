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

import java.util.List;

import me.golemcore.plugin.api.runtime.model.RuntimeConfig;

/**
 * Host contract for runtime configuration access used by plugins.
 */
public interface RuntimeConfigService {

    RuntimeConfig getRuntimeConfig();

    RuntimeConfig getRuntimeConfigForApi();

    void updateRuntimeConfig(RuntimeConfig newConfig);

    boolean isTelegramEnabled();

    String getTelegramToken();

    List<String> getTelegramAllowedUsers();

    boolean isVoiceEnabled();

    String getVoiceApiKey();

    String getVoiceId();

    String getTtsModelId();

    String getSttModelId();

    float getVoiceSpeed();

    boolean isTelegramTranscribeIncomingEnabled();

    String getSttProvider();

    String getTtsProvider();

    String getWhisperSttUrl();

    String getWhisperSttApiKey();

    boolean isToolConfirmationEnabled();

    int getToolConfirmationTimeoutSeconds();

    RuntimeConfig.InviteCode generateInviteCode();

    boolean revokeInviteCode(String code);

    boolean redeemInviteCode(String code, String userId);

    boolean removeTelegramAllowedUser(String userId);
}
