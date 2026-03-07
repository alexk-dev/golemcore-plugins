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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Secret {

    private String value;

    @Builder.Default
    private Boolean encrypted = false;

    @Builder.Default
    private Boolean present = false;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Secret fromJson(Object source) {
        if (source == null) {
            return null;
        }

        if (source instanceof String value) {
            return Secret.builder()
                    .value(value)
                    .encrypted(false)
                    .present(!value.isBlank())
                    .build();
        }

        if (source instanceof Map<?, ?> map) {
            Object valueObj = map.get("value");
            Object encryptedObj = map.get("encrypted");
            Object presentObj = map.get("present");
            String value = valueObj != null ? String.valueOf(valueObj) : null;
            boolean encrypted = encryptedObj instanceof Boolean && (Boolean) encryptedObj;
            boolean present = presentObj instanceof Boolean && (Boolean) presentObj;
            if (!present && value != null && !value.isBlank()) {
                present = true;
            }
            return Secret.builder()
                    .value(value)
                    .encrypted(encrypted)
                    .present(present)
                    .build();
        }

        String value = String.valueOf(source);
        return Secret.builder()
                .value(value)
                .encrypted(false)
                .present(!value.isBlank())
                .build();
    }

    public static Secret of(String value) {
        return fromJson(value);
    }

    public static Secret redacted(Secret source) {
        if (source == null) {
            return null;
        }
        boolean isPresent = Boolean.TRUE.equals(source.getPresent())
                || (source.getValue() != null && !source.getValue().isBlank());
        return Secret.builder()
                .value(null)
                .encrypted(Boolean.TRUE.equals(source.getEncrypted()))
                .present(isPresent)
                .build();
    }

    public static String valueOrEmpty(Secret secret) {
        if (secret == null || secret.getValue() == null) {
            return "";
        }
        return secret.getValue();
    }

    public static boolean hasValue(Secret secret) {
        return secret != null && secret.getValue() != null && !secret.getValue().isBlank();
    }
}
