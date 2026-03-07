package me.golemcore.plugins.golemcore.mail.tool;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import me.golemcore.plugins.golemcore.mail.MailPluginConfig;
import me.golemcore.plugins.golemcore.mail.MailPluginConfigService;
import me.golemcore.plugins.golemcore.mail.support.HtmlSanitizer;
import me.golemcore.plugins.golemcore.mail.support.MailSecurity;
import me.golemcore.plugins.golemcore.mail.support.MailSessionFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@SuppressWarnings({ "PMD.ReplaceJavaUtilDate", "PMD.CloseResource", "PMD.UseTryWithResources" })
public class ImapToolProvider implements ToolProvider {

    private static final String PARAM_TYPE = "type";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_OBJECT = "object";
    private static final String SCHEMA_DESC = "description";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_FOLDER = "folder";
    private static final String PARAM_UID = "uid";
    private static final String PARAM_OFFSET = "offset";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_FROM = "from";
    private static final String PARAM_SUBJECT = "subject";
    private static final String PARAM_SINCE = "since";
    private static final String PARAM_BEFORE = "before";
    private static final String PARAM_UNSEEN = "unseen";
    private static final String DEFAULT_FOLDER = "INBOX";
    private static final int MAX_MULTIPART_DEPTH = 10;
    private static final int SINGLE_TERM = 1;
    private static final String BODY_TRUNCATED_SUFFIX = "\n[Body truncated]";
    private static final String NO_SUBJECT = "(no subject)";
    private static final String UNKNOWN_DATE = "unknown";

    private final MailPluginConfigService configService;

    public ImapToolProvider(MailPluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean isEnabled() {
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        return Boolean.TRUE.equals(config.getEnabled())
                && config.getHost() != null && !config.getHost().isBlank()
                && config.getUsername() != null && !config.getUsername().isBlank();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("imap")
                .description("""
                        Read email via IMAP. Operations: list_folders, list_messages, read_message, search_messages.
                        Messages are referenced by UID (persistent across sessions).
                        All folder names are case-sensitive. Default folder is INBOX.
                        """)
                .inputSchema(Map.of(
                        PARAM_TYPE, TYPE_OBJECT,
                        "properties", Map.of(
                                PARAM_OPERATION, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        "enum", List.of("list_folders", "list_messages", "read_message",
                                                "search_messages"),
                                        SCHEMA_DESC, "Operation to perform"),
                                PARAM_FOLDER, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        SCHEMA_DESC, "IMAP folder name (default: INBOX)"),
                                PARAM_UID, Map.of(
                                        PARAM_TYPE, TYPE_INTEGER,
                                        SCHEMA_DESC, "Message UID (for read_message)"),
                                PARAM_OFFSET, Map.of(
                                        PARAM_TYPE, TYPE_INTEGER,
                                        SCHEMA_DESC, "Offset for pagination (default: 0)"),
                                PARAM_LIMIT, Map.of(
                                        PARAM_TYPE, TYPE_INTEGER,
                                        SCHEMA_DESC, "Max messages to return (default: 20)"),
                                PARAM_FROM, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        SCHEMA_DESC, "Search: filter by sender address"),
                                PARAM_SUBJECT, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        SCHEMA_DESC, "Search: filter by subject"),
                                PARAM_SINCE, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        SCHEMA_DESC, "Search: messages since date (yyyy-MM-dd)"),
                                PARAM_BEFORE, Map.of(
                                        PARAM_TYPE, TYPE_STRING,
                                        SCHEMA_DESC, "Search: messages before date (yyyy-MM-dd)"),
                                PARAM_UNSEEN, Map.of(
                                        PARAM_TYPE, TYPE_BOOLEAN,
                                        SCHEMA_DESC, "Search: only unread messages")),
                        "required", List.of(PARAM_OPERATION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            String operation = parameters.get(PARAM_OPERATION) instanceof String value ? value : null;
            if (operation == null || operation.isBlank()) {
                return ToolResult.failure("Missing required parameter: operation");
            }

            try {
                return switch (operation) {
                case "list_folders" -> listFolders();
                case "list_messages" -> listMessages(parameters);
                case "read_message" -> readMessage(parameters);
                case "search_messages" -> searchMessages(parameters);
                default -> ToolResult.failure("Unknown operation: " + operation);
                };
            } catch (AuthenticationFailedException e) {
                return ToolResult.failure("IMAP authentication failed. Check username and password.");
            } catch (MessagingException e) {
                return ToolResult.failure("IMAP error: " + sanitizeError(e.getMessage()));
            } catch (Exception e) {
                return ToolResult.failure("IMAP error: " + sanitizeError(e.getMessage()));
            }
        });
    }

    Store connectStore() throws MessagingException {
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        MailSecurity security = MailSecurity.fromString(config.getSecurity());
        Session session = MailSessionFactory.createImapSession(
                config.getHost(), config.getPort(),
                config.getUsername(), config.getPassword(),
                security, config.getSslTrust(),
                config.getConnectTimeout(), config.getReadTimeout());
        String protocol = security == MailSecurity.SSL ? "imaps" : "imap";
        Store store = session.getStore(protocol);
        store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
        return store;
    }

    private ToolResult listFolders() throws MessagingException {
        try (Store store = connectStore()) {
            Folder[] folders = store.getDefaultFolder().list("*");
            StringBuilder sb = new StringBuilder();
            sb.append("IMAP Folders:\n\n");

            List<Map<String, Object>> folderList = new ArrayList<>();
            for (Folder folder : folders) {
                int type = folder.getType();
                if ((type & Folder.HOLDS_MESSAGES) != 0) {
                    folder.open(Folder.READ_ONLY);
                    int total = folder.getMessageCount();
                    int unread = folder.getUnreadMessageCount();
                    folder.close(false);

                    sb.append(String.format("  %s — %d messages (%d unread)%n", folder.getFullName(), total, unread));
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", folder.getFullName());
                    info.put("total", total);
                    info.put("unread", unread);
                    folderList.add(info);
                } else {
                    sb.append(String.format("  %s (folder only)%n", folder.getFullName()));
                    folderList.add(Map.of("name", folder.getFullName(), "holdsMessages", false));
                }
            }
            return ToolResult.success(sb.toString(), Map.of("folders", folderList));
        }
    }

    private ToolResult listMessages(Map<String, Object> params) throws MessagingException {
        String folderName = getStringParam(params, PARAM_FOLDER, DEFAULT_FOLDER);
        int offset = getIntParam(params, PARAM_OFFSET, 0);
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        int limit = getIntParam(params, PARAM_LIMIT, config.getDefaultMessageLimit());

        try (Store store = connectStore()) {
            Folder folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return ToolResult.failure("Folder not found: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            try {
                return buildMessageList(folder, offset, limit);
            } finally {
                folder.close(false);
            }
        }
    }

    private ToolResult buildMessageList(Folder folder, int offset, int limit) throws MessagingException {
        UIDFolder uidFolder = (UIDFolder) folder;
        int totalMessages = folder.getMessageCount();
        if (totalMessages == 0) {
            return ToolResult.success("No messages in " + folder.getFullName());
        }

        int endIndex = totalMessages - offset;
        int startIndex = Math.max(SINGLE_TERM, endIndex - limit + SINGLE_TERM);
        if (endIndex < SINGLE_TERM) {
            return ToolResult.success("No more messages (offset beyond total).");
        }

        Message[] messages = folder.getMessages(startIndex, endIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Messages in %s (%d total, showing %d-%d):%n%n",
                folder.getFullName(), totalMessages, startIndex, endIndex));

        List<Map<String, Object>> messageList = new ArrayList<>();
        for (int i = messages.length - SINGLE_TERM; i >= 0; i--) {
            Message message = messages[i];
            long messageUid = uidFolder.getUID(message);
            String messageFrom = formatAddress(message.getFrom());
            String messageSubject = message.getSubject() != null ? message.getSubject() : NO_SUBJECT;
            Date sentDate = message.getSentDate();
            boolean seen = message.isSet(Flags.Flag.SEEN);
            boolean flagged = message.isSet(Flags.Flag.FLAGGED);

            String flags = (seen ? "" : "[UNREAD] ") + (flagged ? "[FLAGGED] " : "");
            sb.append(String.format("UID: %d %s%n  From: %s%n  Subject: %s%n  Date: %s%n%n",
                    messageUid, flags, messageFrom, messageSubject,
                    sentDate != null ? sentDate.toString() : UNKNOWN_DATE));

            Map<String, Object> info = new LinkedHashMap<>();
            info.put(PARAM_UID, messageUid);
            info.put(PARAM_FROM, messageFrom);
            info.put(PARAM_SUBJECT, messageSubject);
            info.put("date", sentDate != null ? sentDate.toString() : null);
            info.put("seen", seen);
            info.put("flagged", flagged);
            messageList.add(info);
        }

        return ToolResult.success(sb.toString(), Map.of(
                "messages", messageList,
                "total", totalMessages,
                PARAM_OFFSET, offset,
                PARAM_LIMIT, limit));
    }

    private ToolResult readMessage(Map<String, Object> params) throws MessagingException, IOException {
        String folderName = getStringParam(params, PARAM_FOLDER, DEFAULT_FOLDER);
        Object uidObj = params.get(PARAM_UID);
        if (uidObj == null) {
            return ToolResult.failure("Missing required parameter: uid");
        }
        long uid = ((Number) uidObj).longValue();

        try (Store store = connectStore()) {
            Folder folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return ToolResult.failure("Folder not found: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = (UIDFolder) folder;
                Message message = uidFolder.getMessageByUID(uid);
                if (message == null) {
                    return ToolResult.failure("Message not found with UID: " + uid);
                }
                return buildFullMessage(message, uid);
            } finally {
                folder.close(false);
            }
        }
    }

    private ToolResult buildFullMessage(Message message, long uid) throws MessagingException, IOException {
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        String messageFrom = formatAddress(message.getFrom());
        String to = formatAddresses(message.getRecipients(Message.RecipientType.TO));
        String cc = formatAddresses(message.getRecipients(Message.RecipientType.CC));
        String messageSubject = message.getSubject() != null ? message.getSubject() : NO_SUBJECT;
        Date sentDate = message.getSentDate();
        String messageId = getHeader(message, "Message-ID");

        BodyContent bodyContent = extractBody(message, 0);
        String body = bodyContent.text();
        if (body.length() > config.getMaxBodyLength()) {
            body = body.substring(0, config.getMaxBodyLength()) + BODY_TRUNCATED_SUFFIX;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("UID: %d%n", uid));
        sb.append(String.format("From: %s%n", messageFrom));
        sb.append(String.format("To: %s%n", to));
        if (!cc.isEmpty()) {
            sb.append(String.format("Cc: %s%n", cc));
        }
        sb.append(String.format("Subject: %s%n", messageSubject));
        sb.append(String.format("Date: %s%n", sentDate != null ? sentDate.toString() : UNKNOWN_DATE));
        if (messageId != null) {
            sb.append(String.format("Message-ID: %s%n", messageId));
        }
        sb.append(String.format("%n--- Body ---%n%s%n", body));

        if (!bodyContent.attachments().isEmpty()) {
            sb.append(String.format("%n--- Attachments (%d) ---%n", bodyContent.attachments().size()));
            for (Map<String, String> attachment : bodyContent.attachments()) {
                sb.append(String.format("  %s (%s, %s)%n",
                        attachment.get("filename"), attachment.get("contentType"), attachment.get("size")));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(PARAM_UID, uid);
        data.put(PARAM_FROM, messageFrom);
        data.put("to", to);
        data.put("cc", cc);
        data.put(PARAM_SUBJECT, messageSubject);
        data.put("date", sentDate != null ? sentDate.toString() : null);
        data.put("messageId", messageId);
        data.put("attachments", bodyContent.attachments());

        return ToolResult.success(sb.toString(), data);
    }

    private ToolResult searchMessages(Map<String, Object> params) throws MessagingException {
        String folderName = getStringParam(params, PARAM_FOLDER, DEFAULT_FOLDER);
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        int limit = getIntParam(params, PARAM_LIMIT, config.getDefaultMessageLimit());

        List<SearchTerm> terms = new ArrayList<>();
        String from = getStringParam(params, PARAM_FROM, null);
        if (from != null) {
            terms.add(new FromStringTerm(from));
        }
        String subject = getStringParam(params, PARAM_SUBJECT, null);
        if (subject != null) {
            terms.add(new SubjectTerm(subject));
        }
        String since = getStringParam(params, PARAM_SINCE, null);
        if (since != null) {
            Date sinceDate = parseDate(since);
            if (sinceDate != null) {
                terms.add(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
            }
        }
        String before = getStringParam(params, PARAM_BEFORE, null);
        if (before != null) {
            Date beforeDate = parseDate(before);
            if (beforeDate != null) {
                terms.add(new ReceivedDateTerm(ComparisonTerm.LE, beforeDate));
            }
        }
        if (Boolean.TRUE.equals(params.get(PARAM_UNSEEN))) {
            terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        }
        if (terms.isEmpty()) {
            return ToolResult
                    .failure("At least one search criterion is required (from, subject, since, before, unseen)");
        }

        SearchTerm searchTerm = terms.size() == SINGLE_TERM ? terms.getFirst()
                : new AndTerm(terms.toArray(new SearchTerm[0]));

        try (Store store = connectStore()) {
            Folder folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return ToolResult.failure("Folder not found: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = (UIDFolder) folder;
                Message[] results = folder.search(searchTerm);
                if (results.length == 0) {
                    return ToolResult.success("No messages found matching search criteria.");
                }

                int startIdx = Math.max(0, results.length - limit);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Search results in %s (%d found, showing %d):%n%n",
                        folderName, results.length, Math.min(results.length, limit)));

                List<Map<String, Object>> messageList = new ArrayList<>();
                for (int i = results.length - SINGLE_TERM; i >= startIdx; i--) {
                    Message message = results[i];
                    long messageUid = uidFolder.getUID(message);
                    String messageFrom = formatAddress(message.getFrom());
                    String messageSubject = message.getSubject() != null ? message.getSubject() : NO_SUBJECT;
                    Date sentDate = message.getSentDate();
                    boolean seen = message.isSet(Flags.Flag.SEEN);

                    sb.append(String.format("UID: %d %s%n  From: %s%n  Subject: %s%n  Date: %s%n%n",
                            messageUid, seen ? "" : "[UNREAD]", messageFrom, messageSubject,
                            sentDate != null ? sentDate.toString() : UNKNOWN_DATE));

                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put(PARAM_UID, messageUid);
                    info.put(PARAM_FROM, messageFrom);
                    info.put(PARAM_SUBJECT, messageSubject);
                    info.put("date", sentDate != null ? sentDate.toString() : null);
                    info.put("seen", seen);
                    messageList.add(info);
                }
                return ToolResult.success(sb.toString(), Map.of("messages", messageList, "totalFound", results.length));
            } finally {
                folder.close(false);
            }
        }
    }

    private BodyContent extractBody(Part part, int depth) throws MessagingException, IOException {
        if (depth > MAX_MULTIPART_DEPTH) {
            return new BodyContent("[Content too deeply nested]", List.of());
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return new BodyContent(content != null ? content.toString() : "", List.of());
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return new BodyContent(HtmlSanitizer.stripHtml(content != null ? content.toString() : ""), List.of());
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String plainText = null;
            String htmlText = null;
            List<Map<String, String>> attachments = new ArrayList<>();

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String disposition = bodyPart.getDisposition();

                if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                        || (disposition != null && bodyPart.getFileName() != null)) {
                    Map<String, String> attachment = new LinkedHashMap<>();
                    attachment.put("filename", bodyPart.getFileName() != null ? bodyPart.getFileName() : UNKNOWN_DATE);
                    attachment.put("contentType", bodyPart.getContentType());
                    attachment.put("size", String.valueOf(bodyPart.getSize()));
                    attachments.add(attachment);
                } else {
                    BodyContent nested = extractBody(bodyPart, depth + SINGLE_TERM);
                    attachments.addAll(nested.attachments());
                    if (bodyPart.isMimeType("text/plain") && plainText == null) {
                        plainText = nested.text();
                    } else if (bodyPart.isMimeType("text/html") && htmlText == null) {
                        htmlText = nested.text();
                    } else if (bodyPart.isMimeType("multipart/*") && plainText == null && !nested.text().isEmpty()) {
                        plainText = nested.text();
                    }
                }
            }

            String text = plainText != null ? plainText : (htmlText != null ? htmlText : "");
            return new BodyContent(text, attachments);
        }
        return new BodyContent("", List.of());
    }

    private String formatAddress(jakarta.mail.Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return ((InternetAddress) addresses[0]).toUnicodeString();
    }

    private String formatAddresses(jakarta.mail.Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return Arrays.stream(addresses)
                .map(address -> ((InternetAddress) address).toUnicodeString())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String getHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    private static String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return defaultValue;
    }

    private static int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(dateStr);
        } catch (Exception ignored) {
            return null;
        }
    }

    String sanitizeError(String message) {
        if (message == null) {
            return "Unknown error";
        }
        MailPluginConfig.ImapConfig config = getResolvedConfig();
        String sanitized = message;
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            sanitized = sanitized.replace(config.getUsername(), "***");
        }
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            sanitized = sanitized.replace(config.getPassword(), "***");
        }
        return sanitized;
    }

    private MailPluginConfig.ImapConfig getResolvedConfig() {
        return configService.getConfig().getImap();
    }

    private record BodyContent(String text, List<Map<String, String>> attachments) {
    }
}
