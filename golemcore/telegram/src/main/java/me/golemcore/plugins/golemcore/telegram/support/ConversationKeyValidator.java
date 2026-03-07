package me.golemcore.plugins.golemcore.telegram.support;

import java.util.Comparator;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import me.golemcore.plugin.api.extension.model.AgentSession;

public final class ConversationKeyValidator {

    private static final Pattern STRICT_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{8,64}$");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private ConversationKeyValidator() {
    }

    public static boolean isLegacyCompatibleConversationKey(String value) {
        String normalized = normalize(value);
        return normalized != null && LEGACY_PATTERN.matcher(normalized).matches();
    }

    public static String normalizeForActivationOrThrow(
            String value,
            Predicate<String> legacyExistsPredicate) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("conversationKey must match ^[a-zA-Z0-9_-]{8,64}$");
        }
        if (STRICT_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        if (LEGACY_PATTERN.matcher(normalized).matches()
                && legacyExistsPredicate != null
                && legacyExistsPredicate.test(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("conversationKey must match ^[a-zA-Z0-9_-]{8,64}$");
    }

    public static Comparator<AgentSession> byRecentActivity() {
        return Comparator.comparing(
                (AgentSession session) -> session.getUpdatedAt() != null ? session.getUpdatedAt()
                        : session.getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
    }

    private static String normalize(String value) {
        if (StringValueSupport.isBlank(value)) {
            return null;
        }
        String candidate = value.trim();
        return candidate.isEmpty() ? null : candidate;
    }
}
