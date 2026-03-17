package me.golemcore.plugins.golemcore.slack.support;

import java.util.regex.Pattern;

public final class SlackTransportChatIds {

    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("^[CDG][A-Z0-9]+$");

    private SlackTransportChatIds() {
    }

    public static boolean looksLikeSlackTransportChatId(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return false;
        }

        int separatorIndex = normalized.indexOf("::");
        String channelId = separatorIndex >= 0
                ? normalized.substring(0, separatorIndex)
                : normalized;
        return CHANNEL_ID_PATTERN.matcher(channelId).matches();
    }
}
