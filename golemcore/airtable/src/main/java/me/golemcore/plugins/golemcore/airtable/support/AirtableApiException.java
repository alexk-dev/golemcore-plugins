package me.golemcore.plugins.golemcore.airtable.support;

public class AirtableApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public AirtableApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
