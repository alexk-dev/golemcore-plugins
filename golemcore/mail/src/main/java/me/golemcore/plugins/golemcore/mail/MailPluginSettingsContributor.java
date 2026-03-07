package me.golemcore.plugins.golemcore.mail;

import me.golemcore.plugin.api.extension.spi.PluginActionResult;
import me.golemcore.plugin.api.extension.spi.PluginSettingsCatalogItem;
import me.golemcore.plugin.api.extension.spi.PluginSettingsContributor;
import me.golemcore.plugin.api.extension.spi.PluginSettingsField;
import me.golemcore.plugin.api.extension.spi.PluginSettingsSection;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MailPluginSettingsContributor implements PluginSettingsContributor {

    private static final String SECTION_KEY = "main";

    private final MailPluginConfigService configService;

    public MailPluginSettingsContributor(MailPluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String getPluginId() {
        return MailPluginConfigService.PLUGIN_ID;
    }

    @Override
    public List<PluginSettingsCatalogItem> getCatalogItems() {
        return List.of(PluginSettingsCatalogItem.builder()
                .pluginId(MailPluginConfigService.PLUGIN_ID)
                .pluginName("mail")
                .provider("golemcore")
                .sectionKey(SECTION_KEY)
                .title("Email (IMAP/SMTP)")
                .description("Mailbox credentials and transport settings for mail tools.")
                .blockKey("tools")
                .blockTitle("Tools")
                .blockDescription("Tool-specific runtime behavior and integrations")
                .order(33)
                .build());
    }

    @Override
    public PluginSettingsSection getSection(String sectionKey) {
        requireSection(sectionKey);
        MailPluginConfig config = configService.getConfig();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("imapEnabled", Boolean.TRUE.equals(config.getImap().getEnabled()));
        values.put("imapHost", orEmpty(config.getImap().getHost()));
        values.put("imapPort", config.getImap().getPort());
        values.put("imapUsername", orEmpty(config.getImap().getUsername()));
        values.put("imapPassword", "");
        values.put("imapSecurity", config.getImap().getSecurity());
        values.put("imapSslTrust", orEmpty(config.getImap().getSslTrust()));
        values.put("imapConnectTimeout", config.getImap().getConnectTimeout());
        values.put("imapReadTimeout", config.getImap().getReadTimeout());
        values.put("imapMaxBodyLength", config.getImap().getMaxBodyLength());
        values.put("imapDefaultMessageLimit", config.getImap().getDefaultMessageLimit());
        values.put("smtpEnabled", Boolean.TRUE.equals(config.getSmtp().getEnabled()));
        values.put("smtpHost", orEmpty(config.getSmtp().getHost()));
        values.put("smtpPort", config.getSmtp().getPort());
        values.put("smtpUsername", orEmpty(config.getSmtp().getUsername()));
        values.put("smtpPassword", "");
        values.put("smtpSecurity", config.getSmtp().getSecurity());
        values.put("smtpSslTrust", orEmpty(config.getSmtp().getSslTrust()));
        values.put("smtpConnectTimeout", config.getSmtp().getConnectTimeout());
        values.put("smtpReadTimeout", config.getSmtp().getReadTimeout());

        return PluginSettingsSection.builder()
                .title("Email")
                .description("Configure IMAP read access and SMTP sending in the isolated mail plugin.")
                .fields(List.of(
                        field("imapEnabled", "boolean", "Enable IMAP", "Allow mailbox reads through IMAP."),
                        field("imapHost", "text", "IMAP Host", "IMAP server hostname (e.g. imap.gmail.com)."),
                        numberField("imapPort", "IMAP Port", "IMAP server port.", 1, 65535, 1),
                        field("imapUsername", "text", "IMAP Username", "Mailbox username."),
                        secretField("imapPassword", "IMAP Password", "Mailbox password or app password."),
                        selectField("imapSecurity", "IMAP Security", "Connection security mode.",
                                List.of("ssl", "starttls", "none")),
                        field("imapSslTrust", "text", "IMAP SSL Trust", "Optional '*' or hostname allowlist."),
                        numberField("imapConnectTimeout", "IMAP Connect Timeout", "Connection timeout in ms.", 1000,
                                120000, 1000),
                        numberField("imapReadTimeout", "IMAP Read Timeout", "Read timeout in ms.", 1000, 120000, 1000),
                        numberField("imapMaxBodyLength", "IMAP Max Body Length",
                                "Maximum plain-text body characters returned for a message.", 1000, 200000, 1000),
                        numberField("imapDefaultMessageLimit", "IMAP Default Message Limit",
                                "Default page size for list/search operations.", 1, 200, 1),
                        field("smtpEnabled", "boolean", "Enable SMTP", "Allow email sending through SMTP."),
                        field("smtpHost", "text", "SMTP Host", "SMTP server hostname (e.g. smtp.gmail.com)."),
                        numberField("smtpPort", "SMTP Port", "SMTP server port.", 1, 65535, 1),
                        field("smtpUsername", "text", "SMTP Username", "Mailbox username."),
                        secretField("smtpPassword", "SMTP Password", "Mailbox password or app password."),
                        selectField("smtpSecurity", "SMTP Security", "Connection security mode.",
                                List.of("ssl", "starttls", "none")),
                        field("smtpSslTrust", "text", "SMTP SSL Trust", "Optional '*' or hostname allowlist."),
                        numberField("smtpConnectTimeout", "SMTP Connect Timeout", "Connection timeout in ms.", 1000,
                                120000, 1000),
                        numberField("smtpReadTimeout", "SMTP Read Timeout", "Read timeout in ms.", 1000, 120000, 1000)))
                .values(values)
                .build();
    }

    @Override
    public PluginSettingsSection saveSection(String sectionKey, Map<String, Object> values) {
        requireSection(sectionKey);
        MailPluginConfig config = configService.getConfig();
        MailPluginConfig.ImapConfig imap = config.getImap();
        MailPluginConfig.SmtpConfig smtp = config.getSmtp();

        imap.setEnabled(readBoolean(values, "imapEnabled", false));
        imap.setHost(readString(values, "imapHost", null));
        imap.setPort(readInteger(values, "imapPort", imap.getPort()));
        imap.setUsername(readString(values, "imapUsername", null));
        String imapPassword = readString(values, "imapPassword", null);
        if (imapPassword != null && !imapPassword.isBlank()) {
            imap.setPassword(imapPassword);
        }
        imap.setSecurity(readString(values, "imapSecurity", imap.getSecurity()));
        imap.setSslTrust(readString(values, "imapSslTrust", null));
        imap.setConnectTimeout(readInteger(values, "imapConnectTimeout", imap.getConnectTimeout()));
        imap.setReadTimeout(readInteger(values, "imapReadTimeout", imap.getReadTimeout()));
        imap.setMaxBodyLength(readInteger(values, "imapMaxBodyLength", imap.getMaxBodyLength()));
        imap.setDefaultMessageLimit(readInteger(values, "imapDefaultMessageLimit", imap.getDefaultMessageLimit()));

        smtp.setEnabled(readBoolean(values, "smtpEnabled", false));
        smtp.setHost(readString(values, "smtpHost", null));
        smtp.setPort(readInteger(values, "smtpPort", smtp.getPort()));
        smtp.setUsername(readString(values, "smtpUsername", null));
        String smtpPassword = readString(values, "smtpPassword", null);
        if (smtpPassword != null && !smtpPassword.isBlank()) {
            smtp.setPassword(smtpPassword);
        }
        smtp.setSecurity(readString(values, "smtpSecurity", smtp.getSecurity()));
        smtp.setSslTrust(readString(values, "smtpSslTrust", null));
        smtp.setConnectTimeout(readInteger(values, "smtpConnectTimeout", smtp.getConnectTimeout()));
        smtp.setReadTimeout(readInteger(values, "smtpReadTimeout", smtp.getReadTimeout()));

        configService.save(config);
        return getSection(sectionKey);
    }

    @Override
    public PluginActionResult executeAction(String sectionKey, String actionId, Map<String, Object> payload) {
        requireSection(sectionKey);
        throw new IllegalArgumentException("Unknown mail plugin action: " + actionId);
    }

    private PluginSettingsField field(String key, String type, String label, String description) {
        return PluginSettingsField.builder()
                .key(key)
                .type(type)
                .label(label)
                .description(description)
                .build();
    }

    private PluginSettingsField secretField(String key, String label, String description) {
        return PluginSettingsField.builder()
                .key(key)
                .type("secret")
                .label(label)
                .description(description)
                .build();
    }

    private PluginSettingsField selectField(String key, String label, String description, List<String> options) {
        return PluginSettingsField.builder()
                .key(key)
                .type("select")
                .label(label)
                .description(description)
                .options(options.stream()
                        .map(value -> me.golemcore.plugin.api.extension.spi.PluginSettingsFieldOption.builder()
                                .value(value)
                                .label(value.toUpperCase(java.util.Locale.ROOT))
                                .build())
                        .toList())
                .build();
    }

    private PluginSettingsField numberField(String key, String label, String description,
            double min, double max, double step) {
        return PluginSettingsField.builder()
                .key(key)
                .type("number")
                .label(label)
                .description(description)
                .min(min)
                .max(max)
                .step(step)
                .build();
    }

    private void requireSection(String sectionKey) {
        if (!SECTION_KEY.equals(sectionKey)) {
            throw new IllegalArgumentException("Unknown mail settings section: " + sectionKey);
        }
    }

    private boolean readBoolean(Map<String, Object> values, String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private int readInteger(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String readString(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? defaultValue : trimmed;
        }
        return defaultValue;
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }
}
