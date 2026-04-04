package me.golemcore.plugins.golemcore.airtable.support;

public class AirtableTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AirtableTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
