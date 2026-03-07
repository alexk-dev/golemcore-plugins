package me.golemcore.plugins.golemcore.mail.support;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.util.Properties;

public final class MailSessionFactory {

    private static final String MAIL_PREFIX = "mail.";
    private static final String TRUE_VALUE = "true";

    private MailSessionFactory() {
    }

    public static Session createImapSession(String host, int port, String username, String password,
            MailSecurity security, String sslTrust, int connectTimeout, int readTimeout) {
        Properties props = new Properties();
        String protocol = security == MailSecurity.SSL ? "imaps" : "imap";
        String prefix = MAIL_PREFIX + protocol + ".";

        props.put("mail.store.protocol", protocol);
        props.put(prefix + "host", host);
        props.put(prefix + "port", Integer.toString(port));
        props.put(prefix + "connectiontimeout", Integer.toString(connectTimeout));
        props.put(prefix + "timeout", Integer.toString(readTimeout));

        if (security == MailSecurity.SSL) {
            props.put("mail.imaps.ssl.enable", TRUE_VALUE);
        } else if (security == MailSecurity.STARTTLS) {
            props.put("mail.imap.starttls.enable", TRUE_VALUE);
            props.put("mail.imap.starttls.required", TRUE_VALUE);
        }

        if (sslTrust != null && !sslTrust.isBlank()) {
            props.put(prefix + "ssl.trust", sslTrust);
        }

        return Session.getInstance(props, createAuthenticator(username, password));
    }

    public static Session createSmtpSession(String host, int port, String username, String password,
            MailSecurity security, String sslTrust, int connectTimeout, int readTimeout) {
        Properties props = new Properties();
        String protocol = security == MailSecurity.SSL ? "smtps" : "smtp";
        String prefix = MAIL_PREFIX + protocol + ".";

        props.put("mail.transport.protocol", protocol);
        props.put(prefix + "host", host);
        props.put(prefix + "port", Integer.toString(port));
        props.put(prefix + "auth", TRUE_VALUE);
        props.put(prefix + "connectiontimeout", Integer.toString(connectTimeout));
        props.put(prefix + "timeout", Integer.toString(readTimeout));

        if (security == MailSecurity.SSL) {
            props.put("mail.smtps.ssl.enable", TRUE_VALUE);
        } else if (security == MailSecurity.STARTTLS) {
            props.put("mail.smtp.starttls.enable", TRUE_VALUE);
            props.put("mail.smtp.starttls.required", TRUE_VALUE);
        }

        if (sslTrust != null && !sslTrust.isBlank()) {
            props.put(prefix + "ssl.trust", sslTrust);
        }

        return Session.getInstance(props, createAuthenticator(username, password));
    }

    private static Authenticator createAuthenticator(String username, String password) {
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };
    }
}
