package me.golemcore.plugins.golemcore.telegram.support;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.golemcore.plugin.api.extension.model.AgentSession;
import me.golemcore.plugin.api.extension.model.ContextAttributes;
import me.golemcore.plugin.api.runtime.model.SessionIdentity;

public final class SessionIdentitySupport {

    private static final String SESSION_ID_SEPARATOR = ":";

    private SessionIdentitySupport() {
    }

    public static String resolveConversationKey(AgentSession session) {
        if (session == null) {
            return "";
        }

        String metadataConversation = readMetadataString(session, ContextAttributes.CONVERSATION_KEY);
        if (!StringValueSupport.isBlank(metadataConversation)) {
            return metadataConversation;
        }

        String sessionId = session.getId();
        if (!StringValueSupport.isBlank(sessionId)) {
            int index = sessionId.indexOf(SESSION_ID_SEPARATOR);
            if (index >= 0 && index + 1 < sessionId.length()) {
                return sessionId.substring(index + 1);
            }
        }

        if (!StringValueSupport.isBlank(session.getChatId())) {
            return session.getChatId();
        }
        return "";
    }

    public static SessionIdentity resolveSessionIdentity(String channelType, String conversationKey) {
        if (StringValueSupport.isBlank(channelType) || StringValueSupport.isBlank(conversationKey)) {
            return null;
        }
        return new SessionIdentity(channelType.trim().toLowerCase(Locale.ROOT), conversationKey.trim());
    }

    public static String resolveTransportChatId(AgentSession session) {
        if (session == null) {
            return "";
        }

        String metadataTransport = readMetadataString(session, ContextAttributes.TRANSPORT_CHAT_ID);
        if (!StringValueSupport.isBlank(metadataTransport)) {
            return metadataTransport;
        }

        if (!StringValueSupport.isBlank(session.getChatId())) {
            return session.getChatId();
        }
        return "";
    }

    public static boolean belongsToTransport(AgentSession session, String transportChatId) {
        if (StringValueSupport.isBlank(transportChatId)) {
            return false;
        }
        return transportChatId.equals(resolveTransportChatId(session));
    }

    public static void bindTransportAndConversation(
            AgentSession session,
            String transportChatId,
            String conversationKey) {
        if (session == null) {
            return;
        }

        if (session.getMetadata() == null) {
            session.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = session.getMetadata();
        if (!StringValueSupport.isBlank(transportChatId)
                && !transportChatId.equals(readMetadataString(session, ContextAttributes.TRANSPORT_CHAT_ID))) {
            metadata.put(ContextAttributes.TRANSPORT_CHAT_ID, transportChatId);
        }
        if (!StringValueSupport.isBlank(conversationKey)
                && !conversationKey.equals(readMetadataString(session, ContextAttributes.CONVERSATION_KEY))) {
            metadata.put(ContextAttributes.CONVERSATION_KEY, conversationKey);
        }
    }

    public static String readMetadataString(AgentSession session, String key) {
        if (session == null || session.getMetadata() == null || StringValueSupport.isBlank(key)) {
            return null;
        }
        Object value = session.getMetadata().get(key);
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }
}
