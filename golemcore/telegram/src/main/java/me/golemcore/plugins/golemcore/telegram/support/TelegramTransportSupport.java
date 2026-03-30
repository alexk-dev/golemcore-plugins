package me.golemcore.plugins.golemcore.telegram.support;

public final class TelegramTransportSupport {

    private static final String THREAD_MARKER = "#thread:";

    private TelegramTransportSupport() {
    }

    public static String buildTransportChatId(String rawChatId, Integer messageThreadId, String conversationScope) {
        if (StringValueSupport.isBlank(rawChatId)) {
            return rawChatId;
        }
        if (!isThreadScope(conversationScope) || messageThreadId == null || messageThreadId <= 0) {
            return rawChatId.trim();
        }
        return rawChatId.trim() + THREAD_MARKER + messageThreadId;
    }

    public static String resolveRawChatId(String transportChatId) {
        if (StringValueSupport.isBlank(transportChatId)) {
            return transportChatId;
        }
        String trimmed = transportChatId.trim();
        int markerIndex = trimmed.indexOf(THREAD_MARKER);
        if (markerIndex < 0) {
            return trimmed;
        }
        return trimmed.substring(0, markerIndex);
    }

    public static Integer resolveThreadId(String transportChatId) {
        if (StringValueSupport.isBlank(transportChatId)) {
            return null;
        }
        String trimmed = transportChatId.trim();
        int markerIndex = trimmed.indexOf(THREAD_MARKER);
        if (markerIndex < 0) {
            return null;
        }
        String value = trimmed.substring(markerIndex + THREAD_MARKER.length());
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isThreadScoped(String transportChatId) {
        return resolveThreadId(transportChatId) != null;
    }

    public static boolean isThreadScope(String conversationScope) {
        return conversationScope != null && "thread".equalsIgnoreCase(conversationScope.trim());
    }
}
