package me.golemcore.plugins.golemcore.mail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailPluginConfig {

    @Builder.Default
    private ImapConfig imap = new ImapConfig();

    @Builder.Default
    private SmtpConfig smtp = new SmtpConfig();

    public void normalize() {
        if (imap == null) {
            imap = new ImapConfig();
        }
        if (smtp == null) {
            smtp = new SmtpConfig();
        }
        imap.normalize();
        smtp.normalize();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImapConfig {
        @Builder.Default
        private Boolean enabled = false;
        private String host;
        @Builder.Default
        private Integer port = 993;
        private String username;
        private String password;
        @Builder.Default
        private String security = "ssl";
        private String sslTrust;
        @Builder.Default
        private Integer connectTimeout = 10_000;
        @Builder.Default
        private Integer readTimeout = 30_000;
        @Builder.Default
        private Integer maxBodyLength = 50_000;
        @Builder.Default
        private Integer defaultMessageLimit = 20;

        public void normalize() {
            if (enabled == null) {
                enabled = false;
            }
            if (port == null || port <= 0) {
                port = 993;
            }
            if (security == null || security.isBlank()) {
                security = "ssl";
            }
            if (connectTimeout == null || connectTimeout <= 0) {
                connectTimeout = 10_000;
            }
            if (readTimeout == null || readTimeout <= 0) {
                readTimeout = 30_000;
            }
            if (maxBodyLength == null || maxBodyLength <= 0) {
                maxBodyLength = 50_000;
            }
            if (defaultMessageLimit == null || defaultMessageLimit <= 0) {
                defaultMessageLimit = 20;
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SmtpConfig {
        @Builder.Default
        private Boolean enabled = false;
        private String host;
        @Builder.Default
        private Integer port = 587;
        private String username;
        private String password;
        @Builder.Default
        private String security = "starttls";
        private String sslTrust;
        @Builder.Default
        private Integer connectTimeout = 10_000;
        @Builder.Default
        private Integer readTimeout = 30_000;

        public void normalize() {
            if (enabled == null) {
                enabled = false;
            }
            if (port == null || port <= 0) {
                port = 587;
            }
            if (security == null || security.isBlank()) {
                security = "starttls";
            }
            if (connectTimeout == null || connectTimeout <= 0) {
                connectTimeout = 10_000;
            }
            if (readTimeout == null || readTimeout <= 0) {
                readTimeout = 30_000;
            }
        }
    }
}
