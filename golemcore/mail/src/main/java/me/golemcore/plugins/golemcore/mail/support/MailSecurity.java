package me.golemcore.plugins.golemcore.mail.support;

import java.util.Locale;

public enum MailSecurity {
    SSL, STARTTLS, NONE;

    public static MailSecurity fromString(String value) {
        if (value == null || value.isBlank()) {
            return SSL;
        }
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
